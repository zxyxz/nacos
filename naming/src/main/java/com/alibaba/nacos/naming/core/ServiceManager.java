/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeer;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeerSet;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.pojo.IpAppInfo;
import com.alibaba.nacos.naming.pojo.ServiceAppInfo;
import com.alibaba.nacos.naming.push.PushService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.alibaba.nacos.api.common.Constants.NULL_STRING;

/**
 * Core manager storing all services in Nacos.
 *
 * @author nkorange
 */
@Component
public class ServiceManager implements RecordListener<Service> {

    private final String PUB_DATA = "pubData";
    private final String APP_NAME_PRE_SEPARATOR = "&appName=";
    private final String APP_NAME_AFT_SEPARATOR = "&";
    private final String DEFAULT_NAMESPACE = "sofamesh";
    private final String DEFAULT_GROUPNAME = "SOFA";

    /**
     * Map(namespace, Map(group@@serviceName, Service)).
     */
    private final Map<String, Map<String, Service>> serviceMap = new ConcurrentHashMap<>();

    private final LinkedBlockingDeque<ServiceKey> toBeUpdatedServicesQueue = new LinkedBlockingDeque<>(1024 * 1024);

    private final Synchronizer synchronizer = new ServiceStatusSynchronizer();

    private final Lock lock = new ReentrantLock();

    @Resource(name = "consistencyDelegate")
    private ConsistencyService consistencyService;

    private final SwitchDomain switchDomain;

    private final DistroMapper distroMapper;

    private final ServerMemberManager memberManager;

    private final PushService pushService;

    private final RaftPeerSet raftPeerSet;

    private int maxFinalizeCount = 3;

    private final Object putServiceLock = new Object();

    @Value("${nacos.naming.empty-service.auto-clean:false}")
    private boolean emptyServiceAutoClean;

    @Value("${nacos.naming.empty-service.clean.initial-delay-ms:60000}")
    private int cleanEmptyServiceDelay;

    @Value("${nacos.naming.empty-service.clean.period-time-ms:20000}")
    private int cleanEmptyServicePeriod;

    public ServiceManager(SwitchDomain switchDomain, DistroMapper distroMapper, ServerMemberManager memberManager,
            PushService pushService, RaftPeerSet raftPeerSet) {
        this.switchDomain = switchDomain;
        this.distroMapper = distroMapper;
        this.memberManager = memberManager;
        this.pushService = pushService;
        this.raftPeerSet = raftPeerSet;
    }

    /**
     * Init service maneger.
     */
    @PostConstruct
    public void init() {
        GlobalExecutor.scheduleServiceReporter(new ServiceReporter(), 60000, TimeUnit.MILLISECONDS);

        GlobalExecutor.submitServiceUpdateManager(new UpdatedServiceProcessor());

        if (emptyServiceAutoClean) {

            Loggers.SRV_LOG.info("open empty service auto clean job, initialDelay : {} ms, period : {} ms",
                    cleanEmptyServiceDelay, cleanEmptyServicePeriod);

            // delay 60s, period 20s;

            // This task is not recommended to be performed frequently in order to avoid
            // the possibility that the service cache information may just be deleted
            // and then created due to the heartbeat mechanism

            GlobalExecutor.scheduleServiceAutoClean(new EmptyServiceAutoClean(), cleanEmptyServiceDelay,
                    cleanEmptyServicePeriod);
        }

        try {
            Loggers.SRV_LOG.info("listen for service meta change");
            consistencyService.listen(KeyBuilder.SERVICE_META_KEY_PREFIX, this);
        } catch (NacosException e) {
            Loggers.SRV_LOG.error("listen for service meta change failed!");
        }
    }

    public Map<String, Service> chooseServiceMap(String namespaceId) {
        return serviceMap.get(namespaceId);
    }

    /**
     * Add a service into queue to update.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param serverIP    target server ip
     * @param checksum    checksum of service
     */
    public void addUpdatedServiceToQueue(String namespaceId, String serviceName, String serverIP, String checksum) {
        lock.lock();
        try {
            toBeUpdatedServicesQueue
                    .offer(new ServiceKey(namespaceId, serviceName, serverIP, checksum), 5, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            toBeUpdatedServicesQueue.poll();
            toBeUpdatedServicesQueue.add(new ServiceKey(namespaceId, serviceName, serverIP, checksum));
            Loggers.SRV_LOG.error("[DOMAIN-STATUS] Failed to add service to be updatd to queue.", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean interests(String key) {
        return KeyBuilder.matchServiceMetaKey(key) && !KeyBuilder.matchSwitchKey(key);
    }

    @Override
    public boolean matchUnlistenKey(String key) {
        return KeyBuilder.matchServiceMetaKey(key) && !KeyBuilder.matchSwitchKey(key);
    }

    @Override
    public void onChange(String key, Service service) throws Exception {
        try {
            if (service == null) {
                Loggers.SRV_LOG.warn("received empty push from raft, key: {}", key);
                return;
            }

            if (StringUtils.isBlank(service.getNamespaceId())) {
                service.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
            }

            Loggers.RAFT.info("[RAFT-NOTIFIER] datum is changed, key: {}, value: {}", key, service);

            Service oldDom = getService(service.getNamespaceId(), service.getName());

            if (oldDom != null) {
                oldDom.update(service);
                // re-listen to handle the situation when the underlying listener is removed:
                consistencyService
                        .listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), true),
                                oldDom);
                consistencyService
                        .listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), false),
                                oldDom);
            } else {
                putServiceAndInit(service);
            }
        } catch (Throwable e) {
            Loggers.SRV_LOG.error("[NACOS-SERVICE] error while processing service update", e);
        }
    }

    @Override
    public void onDelete(String key) throws Exception {
        String namespace = KeyBuilder.getNamespace(key);
        String name = KeyBuilder.getServiceName(key);
        Service service = chooseServiceMap(namespace).get(name);
        Loggers.RAFT.info("[RAFT-NOTIFIER] datum is deleted, key: {}", key);

        if (service != null) {
            service.destroy();
            consistencyService.remove(KeyBuilder.buildInstanceListKey(namespace, name, true));

            consistencyService.remove(KeyBuilder.buildInstanceListKey(namespace, name, false));

            consistencyService.unListen(KeyBuilder.buildServiceMetaKey(namespace, name), service);
            Loggers.SRV_LOG.info("[DEAD-SERVICE] {}", service.toJson());
        }

        chooseServiceMap(namespace).remove(name);
    }

    private class UpdatedServiceProcessor implements Runnable {

        //get changed service from other server asynchronously
        @Override
        public void run() {
            ServiceKey serviceKey = null;

            try {
                while (true) {
                    try {
                        serviceKey = toBeUpdatedServicesQueue.take();
                    } catch (Exception e) {
                        Loggers.EVT_LOG.error("[UPDATE-DOMAIN] Exception while taking item from LinkedBlockingDeque.");
                    }

                    if (serviceKey == null) {
                        continue;
                    }
                    GlobalExecutor.submitServiceUpdate(new ServiceUpdater(serviceKey));
                }
            } catch (Exception e) {
                Loggers.EVT_LOG.error("[UPDATE-DOMAIN] Exception while update service: {}", serviceKey, e);
            }
        }
    }

    private class ServiceUpdater implements Runnable {

        String namespaceId;

        String serviceName;

        String serverIP;

        public ServiceUpdater(ServiceKey serviceKey) {
            this.namespaceId = serviceKey.getNamespaceId();
            this.serviceName = serviceKey.getServiceName();
            this.serverIP = serviceKey.getServerIP();
        }

        @Override
        public void run() {
            try {
                updatedHealthStatus(namespaceId, serviceName, serverIP);
            } catch (Exception e) {
                Loggers.SRV_LOG
                        .warn("[DOMAIN-UPDATER] Exception while update service: {} from {}, error: {}", serviceName,
                                serverIP, e);
            }
        }
    }

    public RaftPeer getMySelfClusterState() {
        return raftPeerSet.local();
    }

    /**
     * Update health status of instance in service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param serverIP    source server Ip
     */
    public void updatedHealthStatus(String namespaceId, String serviceName, String serverIP) {
        Message msg = synchronizer.get(serverIP, UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName));
        JsonNode serviceJson = JacksonUtils.toObj(msg.getData());

        ArrayNode ipList = (ArrayNode) serviceJson.get("ips");
        Map<String, String> ipsMap = new HashMap<>(ipList.size());
        for (int i = 0; i < ipList.size(); i++) {

            String ip = ipList.get(i).asText();
            String[] strings = ip.split("_");
            ipsMap.put(strings[0], strings[1]);
        }

        Service service = getService(namespaceId, serviceName);

        if (service == null) {
            return;
        }

        boolean changed = false;

        List<Instance> instances = service.allIPs();
        for (Instance instance : instances) {

            boolean valid = Boolean.parseBoolean(ipsMap.get(instance.toIpAddr()));
            if (valid != instance.isHealthy()) {
                changed = true;
                instance.setHealthy(valid);
                Loggers.EVT_LOG.info("{} {SYNC} IP-{} : {}:{}@{}", serviceName,
                        (instance.isHealthy() ? "ENABLED" : "DISABLED"), instance.getIp(), instance.getPort(),
                        instance.getClusterName());
            }
        }

        if (changed) {
            pushService.serviceChanged(service);
            if (Loggers.EVT_LOG.isDebugEnabled()) {
                StringBuilder stringBuilder = new StringBuilder();
                List<Instance> allIps = service.allIPs();
                for (Instance instance : allIps) {
                    stringBuilder.append(instance.toIpAddr()).append("_").append(instance.isHealthy()).append(",");
                }
                Loggers.EVT_LOG
                        .debug("[HEALTH-STATUS-UPDATED] namespace: {}, service: {}, ips: {}", service.getNamespaceId(),
                                service.getName(), stringBuilder.toString());
            }
        }

    }

    public Set<String> getAllServiceNames(String namespaceId) {
        return serviceMap.get(namespaceId).keySet();
    }

    public Map<String, Set<String>> getAllServiceNames() {

        Map<String, Set<String>> namesMap = new HashMap<>(16);
        for (String namespaceId : serviceMap.keySet()) {
            namesMap.put(namespaceId, serviceMap.get(namespaceId).keySet());
        }
        return namesMap;
    }

    public Set<String> getAllNamespaces() {
        return serviceMap.keySet();
    }

    public List<String> getAllServiceNameList(String namespaceId) {
        if (chooseServiceMap(namespaceId) == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(chooseServiceMap(namespaceId).keySet());
    }

    public Map<String, Set<Service>> getResponsibleServices() {
        Map<String, Set<Service>> result = new HashMap<>(16);
        for (String namespaceId : serviceMap.keySet()) {
            result.put(namespaceId, new HashSet<>());
            for (Map.Entry<String, Service> entry : serviceMap.get(namespaceId).entrySet()) {
                Service service = entry.getValue();
                if (distroMapper.responsible(entry.getKey())) {
                    result.get(namespaceId).add(service);
                }
            }
        }
        return result;
    }

    public int getResponsibleServiceCount() {
        int serviceCount = 0;
        for (String namespaceId : serviceMap.keySet()) {
            for (Map.Entry<String, Service> entry : serviceMap.get(namespaceId).entrySet()) {
                if (distroMapper.responsible(entry.getKey())) {
                    serviceCount++;
                }
            }
        }
        return serviceCount;
    }

    public int getResponsibleInstanceCount() {
        Map<String, Set<Service>> responsibleServices = getResponsibleServices();
        int count = 0;
        for (String namespaceId : responsibleServices.keySet()) {
            for (Service service : responsibleServices.get(namespaceId)) {
                count += service.allIPs().size();
            }
        }

        return count;
    }

    /**
     * Fast remove service.
     *
     * <p>Remove service bu async.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @throws Exception exception
     */
    public void easyRemoveService(String namespaceId, String serviceName) throws Exception {

        Service service = getService(namespaceId, serviceName);
        if (service == null) {
            throw new IllegalArgumentException("specified service not exist, serviceName : " + serviceName);
        }

        consistencyService.remove(KeyBuilder.buildServiceMetaKey(namespaceId, serviceName));
    }

    public void addOrReplaceService(Service service) throws NacosException {
        consistencyService.put(KeyBuilder.buildServiceMetaKey(service.getNamespaceId(), service.getName()), service);
    }

    public void createEmptyService(String namespaceId, String serviceName, boolean local) throws NacosException {
        createServiceIfAbsent(namespaceId, serviceName, local, null);
    }

    /**
     * Create service if not exist.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param local       whether create service by local
     * @param cluster     cluster
     * @throws NacosException nacos exception
     */
    public void createServiceIfAbsent(String namespaceId, String serviceName, boolean local, Cluster cluster)
            throws NacosException {
        Service service = getService(namespaceId, serviceName);
        if (service == null) {

            Loggers.SRV_LOG.info("creating empty service {}:{}", namespaceId, serviceName);
            service = new Service();
            service.setName(serviceName);
            service.setNamespaceId(namespaceId);
            service.setGroupName(NamingUtils.getGroupName(serviceName));
            // now validate the service. if failed, exception will be thrown
            service.setLastModifiedMillis(System.currentTimeMillis());
            service.recalculateChecksum();
            if (cluster != null) {
                cluster.setService(service);
                service.getClusterMap().put(cluster.getName(), cluster);
            }
            service.validate();

            putServiceAndInit(service);
            if (!local) {
                addOrReplaceService(service);
            }
        }
    }

    /**
     * Register an instance to a service in AP mode.
     *
     * <p>This method creates service or cluster silently if they don't exist.
     *
     * @param namespaceId id of namespace
     * @param serviceName service name
     * @param instance    instance to register
     * @throws Exception any error occurred in the process
     */
    public void registerInstance(String namespaceId, String serviceName, Instance instance) throws NacosException {

        createEmptyService(namespaceId, serviceName, instance.isEphemeral());

        Service service = getService(namespaceId, serviceName);

        if (service == null) {
            throw new NacosException(NacosException.INVALID_PARAM,
                    "service not found, namespace: " + namespaceId + ", service: " + serviceName);
        }

        addInstance(namespaceId, serviceName, instance.isEphemeral(), instance);
    }

    /**
     * Update instance to service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param instance    instance
     * @throws NacosException nacos exception
     */
    public void updateInstance(String namespaceId, String serviceName, Instance instance) throws NacosException {

        Service service = getService(namespaceId, serviceName);

        if (service == null) {
            throw new NacosException(NacosException.INVALID_PARAM,
                    "service not found, namespace: " + namespaceId + ", service: " + serviceName);
        }

        if (!service.allIPs().contains(instance)) {
            throw new NacosException(NacosException.INVALID_PARAM, "instance not exist: " + instance);
        }

        addInstance(namespaceId, serviceName, instance.isEphemeral(), instance);
    }

    /**
     * Add instance to service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param ephemeral   whether instance is ephemeral
     * @param ips         instances
     * @throws NacosException nacos exception
     */
    public void addInstance(String namespaceId, String serviceName, boolean ephemeral, Instance... ips)
            throws NacosException {

        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, ephemeral);

        Service service = getService(namespaceId, serviceName);

        synchronized (service) {
            List<Instance> instanceList = addIpAddresses(service, ephemeral, ips);

            Instances instances = new Instances();
            instances.setInstanceList(instanceList);

            consistencyService.put(key, instances);
        }
    }

    /**
     * Remove instance from service.
     *
     * @param namespaceId namespace
     * @param serviceName service name
     * @param ephemeral   whether instance is ephemeral
     * @param ips         instances
     * @throws NacosException nacos exception
     */
    public void removeInstance(String namespaceId, String serviceName, boolean ephemeral, Instance... ips)
            throws NacosException {
        Service service = getService(namespaceId, serviceName);

        synchronized (service) {
            removeInstance(namespaceId, serviceName, ephemeral, service, ips);
        }
    }

    private void removeInstance(String namespaceId, String serviceName, boolean ephemeral, Service service,
            Instance... ips) throws NacosException {

        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, ephemeral);

        List<Instance> instanceList = substractIpAddresses(service, ephemeral, ips);

        Instances instances = new Instances();
        instances.setInstanceList(instanceList);

        consistencyService.put(key, instances);
    }

    public Instance getInstance(String namespaceId, String serviceName, String cluster, String ip, int port) {
        Service service = getService(namespaceId, serviceName);
        if (service == null) {
            return null;
        }

        List<String> clusters = new ArrayList<>();
        clusters.add(cluster);

        List<Instance> ips = service.allIPs(clusters);
        if (ips == null || ips.isEmpty()) {
            return null;
        }

        for (Instance instance : ips) {
            if (instance.getIp().equals(ip) && instance.getPort() == port) {
                return instance;
            }
        }

        return null;
    }

    /**
     * Compare and get new instance list.
     *
     * @param service   service
     * @param action    {@link UtilsAndCommons#UPDATE_INSTANCE_ACTION_REMOVE} or {@link UtilsAndCommons#UPDATE_INSTANCE_ACTION_ADD}
     * @param ephemeral whether instance is ephemeral
     * @param ips       instances
     * @return instance list after operation
     * @throws NacosException nacos exception
     */
    public List<Instance> updateIpAddresses(Service service, String action, boolean ephemeral, Instance... ips)
            throws NacosException {

        Datum datum = consistencyService
                .get(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), ephemeral));

        List<Instance> currentIPs = service.allIPs(ephemeral);
        Map<String, Instance> currentInstances = new HashMap<>(currentIPs.size());
        Set<String> currentInstanceIds = Sets.newHashSet();

        for (Instance instance : currentIPs) {
            currentInstances.put(instance.toIpAddr(), instance);
            currentInstanceIds.add(instance.getInstanceId());
        }

        Map<String, Instance> instanceMap;
        if (datum != null) {
            instanceMap = setValid(((Instances) datum.value).getInstanceList(), currentInstances);
        } else {
            instanceMap = new HashMap<>(ips.length);
        }

        for (Instance instance : ips) {
            if (!service.getClusterMap().containsKey(instance.getClusterName())) {
                Cluster cluster = new Cluster(instance.getClusterName(), service);
                cluster.init();
                service.getClusterMap().put(instance.getClusterName(), cluster);
                Loggers.SRV_LOG
                        .warn("cluster: {} not found, ip: {}, will create new cluster with default configuration.",
                                instance.getClusterName(), instance.toJson());
            }

            if (UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE.equals(action)) {
                instanceMap.remove(instance.getDatumKey());
            } else {
                instance.setInstanceId(instance.generateInstanceId(currentInstanceIds));
                instanceMap.put(instance.getDatumKey(), instance);
            }

        }

        if (instanceMap.size() <= 0 && UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD.equals(action)) {
            throw new IllegalArgumentException(
                    "ip list can not be empty, service: " + service.getName() + ", ip list: " + JacksonUtils
                            .toJson(instanceMap.values()));
        }

        return new ArrayList<>(instanceMap.values());
    }

    private List<Instance> substractIpAddresses(Service service, boolean ephemeral, Instance... ips)
            throws NacosException {
        return updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE, ephemeral, ips);
    }

    private List<Instance> addIpAddresses(Service service, boolean ephemeral, Instance... ips) throws NacosException {
        return updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, ephemeral, ips);
    }

    private Map<String, Instance> setValid(List<Instance> oldInstances, Map<String, Instance> map) {

        Map<String, Instance> instanceMap = new HashMap<>(oldInstances.size());
        for (Instance instance : oldInstances) {
            Instance instance1 = map.get(instance.toIpAddr());
            if (instance1 != null) {
                instance.setHealthy(instance1.isHealthy());
                instance.setLastBeat(instance1.getLastBeat());
            }
            instanceMap.put(instance.getDatumKey(), instance);
        }
        return instanceMap;
    }

    public Service getService(String namespaceId, String serviceName) {
        if (serviceMap.get(namespaceId) == null) {
            return null;
        }
        return chooseServiceMap(namespaceId).get(serviceName);
    }

    public boolean containService(String namespaceId, String serviceName) {
        return getService(namespaceId, serviceName) != null;
    }

    /**
     * Put service into manager.
     *
     * @param service service
     */
    public void putService(Service service) {
        if (!serviceMap.containsKey(service.getNamespaceId())) {
            synchronized (putServiceLock) {
                if (!serviceMap.containsKey(service.getNamespaceId())) {
                    serviceMap.put(service.getNamespaceId(), new ConcurrentHashMap<>(16));
                }
            }
        }
        serviceMap.get(service.getNamespaceId()).put(service.getName(), service);
    }

    private void putServiceAndInit(Service service) throws NacosException {
        putService(service);
        service.init();
        consistencyService
                .listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), true), service);
        consistencyService
                .listen(KeyBuilder.buildInstanceListKey(service.getNamespaceId(), service.getName(), false), service);
        Loggers.SRV_LOG.info("[NEW-SERVICE] {}", service.toJson());
    }

    /**
     * Search services.
     *
     * @param namespaceId namespace
     * @param regex       search regex
     * @return list of service which searched
     */
    public List<Service> searchServices(String namespaceId, String regex) {
        List<Service> result = new ArrayList<>();
        for (Map.Entry<String, Service> entry : chooseServiceMap(namespaceId).entrySet()) {
            Service service = entry.getValue();
            String key = service.getName() + ":" + ArrayUtils.toString(service.getOwners());
            if (key.matches(regex)) {
                result.add(service);
            }
        }

        return result;
    }

    public HashMap<String, Object> getServicesList(String instanceId, int pageNo, int pageSize, String serviceName, String appName, String groupName, String namespaceId) {
        //先把DistroFilter改造过的serviceName，还原成本来的值
        serviceName = getServiceName(serviceName);
        HashMap<String, Object> resultMap = new HashMap<>();
        List<Map<String, Object>> resultList = new ArrayList<>();
        List<Instance> allInstance = getAllInstance(namespaceId, groupName, instanceId, appName, serviceName, true);
        //使用set去掉重复的
        Set<ServiceAppInfo> result = new HashSet<>();
        allInstance.forEach(instance -> result.add(new ServiceAppInfo(getServiceName(instance.getServiceName()), getAppName(instance))));
        //分页
        result.stream().sorted(Comparator.comparing(ServiceAppInfo::getServiceName)).skip((pageNo - 1) * pageSize)
            .limit(pageSize)
            .forEach(instance -> {
                Map index = new HashMap();
                index.put("serviceName", instance.getServiceName());
                index.put("pubApp", instance.getAppName());
                index.put("pubCount", getAllInstance(namespaceId, groupName, instanceId, instance.getAppName(), instance.getServiceName(), false).size());
                List<PushService.PushClient> pushClientList = getAllPushClient(namespaceId, groupName, instanceId, instance.getServiceName());
                index.put("subCount", pushClientList.size());
                Set<String> subList = pushClientList.stream().map(x -> String.join("|",x.getApp(),x.getIp())).collect(Collectors.toSet());
                index.put("subList", subList);
                resultList.add(index);
            });
        resultMap.put("list", resultList);
        resultMap.put("count", result.size());
        return resultMap;
    }
    public HashMap<String, Object> getAppsList(String clusterName, String groupName, String namespaceId) {
        HashMap<String, Object> resultMap = new HashMap<>();
        Set<String> resultList = new HashSet<>();
        List<Instance> allInstance = getAllInstance(namespaceId, groupName, clusterName, "", "", false);
        allInstance.forEach(instance -> {
            resultList.add(getAppName(instance));
        });
        List<PushService.PushClient> collect = getAllPushClient(namespaceId, groupName, clusterName, "");
        collect.forEach(pushClient -> {
            String app = pushClient.getApp();
            if(StringUtils.isNotBlank(app)){
                resultList.add(app);
            }
        });
        resultMap.put("apps", resultList);
        return resultMap;
    }
    public HashMap<String, Object> getServicesClientList(String instanceId, String appName, String groupName, String namespaceId) {
        HashMap<String, Object> resultMap = new HashMap<>();
        //根据clusterName,namespaceId,groupName从clientMap筛选出一批client
        List<PushService.PushClient> collect = getAllPushClient(namespaceId, groupName, instanceId, "");
        Set<String> firstServers = new HashSet<>();
        //拿到指定app的client订阅了多少的服务
        collect.stream().filter(x-> appName.equals(x.getApp())).forEach(pushClient -> firstServers.add(getServiceName(pushClient.getServiceName())));
        resultMap.put("servers", firstServers);
        return resultMap;
    }
    public HashMap<String, Object> getServicesServerList(String instanceId, String appName, String groupName, String namespaceId) {
        HashMap<String, Object> resultMap = new HashMap<>();
        List<Instance> allInstance = getAllInstance(namespaceId, groupName, instanceId, appName, "", false);
        Set<String> serverSet = new HashSet<>();
        allInstance.forEach(instance -> serverSet.add(getServiceName(instance.getServiceName())));
        resultMap.put("servers", serverSet);
        return resultMap;
    }
    public HashMap<String, Object> getServicesServerPubDetailList(String instanceId, String serviceName, String groupName, String namespaceId) {
        serviceName = getServiceName(serviceName);
        HashMap<String, Object> resultMap = new HashMap<>();
        List<Instance> allInstance = getAllInstance(namespaceId, groupName, instanceId, "", serviceName, false);
        Set<String> details = new HashSet<>();
        allInstance.forEach(instance -> details.add(instance.getMetadata().get(PUB_DATA)));
        resultMap.put("serviceName", serviceName);
        resultMap.put("details", details);
        return resultMap;
    }
    public HashMap<String, Object> getServicesServerSubDetailList(String instanceId, String serviceName, String groupName, String namespaceId) {
        serviceName = getServiceName(serviceName);
        HashMap<String, Object> resultMap = new HashMap<>();
        List<Map<String, Object>> resultList = new ArrayList<>();
        //根据instanceId,serviceName,groupName,namespaceId筛选一遍client
        List<PushService.PushClient> collect = getAllPushClient(namespaceId, groupName, instanceId, serviceName);
        Set<IpAppInfo> result = new HashSet<>();
        //每一个client都要找到对应service下，所有存在的appName
        collect.forEach(pushClient -> {
            result.add(new IpAppInfo(pushClient.getIp(), pushClient.getApp()));
        });
        result.forEach(s -> {
            Map index = new HashMap();
            index.put("ip", s.getIp());
            index.put("appName", s.getAppName());
            resultList.add(index);
        });
        resultMap.put("serviceName", serviceName);
        resultMap.put("details", resultList);
        return resultMap;
    }
    /**
     * @Description:通过一系列条件筛选，从clientMap里得到pushClientList集合
     * @Param: [namespaceId, groupName, clusterName, serviceName]
     * @return: java.util.List<com.alibaba.nacos.naming.push.PushService.PushClient>
     * @Author: Mr.zengbang
     * @Date: 2021/7/15
     */
    public List<PushService.PushClient> getAllPushClient(String namespaceId, String groupName,
                                                         String clusterName, String serviceName) {
        List<PushService.PushClient> pushClientList = new ArrayList<>();
        //不传则使用默认值
        if (StringUtils.isEmpty(namespaceId)) {
            namespaceId = DEFAULT_NAMESPACE;
        }
        if (StringUtils.isEmpty(groupName)) {
            groupName = DEFAULT_GROUPNAME;
        }
        //获取clientMap里的所有client实例
        ConcurrentMap<String, ConcurrentMap<String, PushService.PushClient>> clientMap = pushService.getClientMap();
        if (null == clientMap) return new ArrayList<>();
        clientMap.keySet().forEach(s -> {
            pushClientList.addAll(clientMap.get(s).values());
        });
        String finalNamespaceId = namespaceId;
        String finalGroupName = groupName;
        List<PushService.PushClient> collect = pushClientList.stream()
            .filter(pushClient -> pushClient.getNamespaceId().equals(finalNamespaceId)
                && getGroupName(pushClient.getServiceName()).equals(finalGroupName)
                && pushClient.getClusters().equals(clusterName))
            .filter(pushClient -> StringUtils.isEmpty(serviceName) || getServiceName(pushClient.getServiceName()).equals(serviceName))
            .collect(Collectors.toList());
        return collect;
    }
    /**
     * @Description:通过一系列条件筛选，从serverMap里得到instance集合
     * @Param: [namespaceId, groupName, clusterName, appName, serviceName, likely]
     * @return: java.util.List<com.alibaba.nacos.naming.core.Instance>
     * @Author: Mr.zengbang
     * @Date: 2021/7/19
     */
    public List<Instance> getAllInstance(String namespaceId, String groupName, String clusterName,
                                         String appName, String serviceName, boolean likely) {
        List<Instance> instanceList = new ArrayList<>();
        List<Cluster> clusterList = new ArrayList<>();
        List<Service> serviceList = new ArrayList<>();
        //不传则使用默认值
        if (StringUtils.isEmpty(namespaceId)) {
            namespaceId = DEFAULT_NAMESPACE;
        }
        if (StringUtils.isEmpty(groupName)) {
            groupName = DEFAULT_GROUPNAME;
        }
        //根据namespaceId获得service
        Map<String, Service> serviceMap = getServiceMap(namespaceId);
        if (null == serviceMap) {
            return new ArrayList<>();
        }
        //根据groupName筛选,装填serviceList
        String finalGroupName = groupName;
        serviceMap.keySet().stream()
            .filter(s -> getGroupName(s).equals(finalGroupName))
            .forEach(s -> serviceList.add(serviceMap.get(s)));
        //根据clusterName筛选,装填clisterList
        serviceList.forEach(service -> {
            service.getClusterMap().keySet().stream()
                .filter(s -> s.equals(clusterName))
                .forEach(s -> clusterList.add(service.getClusterMap().get(s)));
        });
        //装填instanceList
        clusterList.forEach(cluster -> instanceList.addAll(cluster.allIPs()));
        //根据serviceName和AppName进行筛选
        return instanceList.stream()
            .filter(instance -> StringUtils.isEmpty(serviceName) || NULL_STRING.equalsIgnoreCase(serviceName) || (likely ? getServiceName(instance.getServiceName()).contains(serviceName) : getServiceName(instance.getServiceName()).equals(serviceName)))
            .filter(instance -> StringUtils.isEmpty(appName) || NULL_STRING.equalsIgnoreCase(appName) || appName.equals(getAppName(instance)))
            .collect(Collectors.toList());
    }

    /**
     * @Description:截取出metadata里面的appName的值
     * @Param: [pubData]
     * @return: java.lang.String
     * @Author: Mr.zengbang
     * @Date: 2021/5/14
     */
    private String getAppName(Instance instance) {
        String pubData = instance.getMetadata().get(PUB_DATA);
        String index = pubData.substring(pubData.indexOf(APP_NAME_PRE_SEPARATOR) + APP_NAME_PRE_SEPARATOR.length());
        String appName = index.substring(0, index.indexOf(APP_NAME_AFT_SEPARATOR));
        return appName;
    }
    /**
     * @Description:groupName@@serviceName -> groupName
     * @Param: str
     * @return: java.lang.String
     * @Author: Mr.zengbang
     * @Date: 2021/5/17
     */
    private String getGroupName(String str) {
        String groupName = str.split(Constants.SERVICE_INFO_SPLITER)[0];
        return groupName;
    }
    /**
     * @Description:groupName@@serviceName -> serviceName
     * @Param: str
     * @return: java.lang.String
     * @Author: Mr.zengbang
     * @Date: 2021/5/17
     */
    private String getServiceName(String str) {
        return str.split(Constants.SERVICE_INFO_SPLITER)[1];
    }

    public int getServiceCount() {
        int serviceCount = 0;
        for (String namespaceId : serviceMap.keySet()) {
            serviceCount += serviceMap.get(namespaceId).size();
        }
        return serviceCount;
    }

    public int getInstanceCount() {
        int total = 0;
        for (String namespaceId : serviceMap.keySet()) {
            for (Service service : serviceMap.get(namespaceId).values()) {
                total += service.allIPs().size();
            }
        }
        return total;
    }

    public Map<String, Service> getServiceMap(String namespaceId) {
        return serviceMap.get(namespaceId);
    }

    public int getPagedService(String namespaceId, int startPage, int pageSize, String param, String containedInstance,
            List<Service> serviceList, boolean hasIpCount) {

        List<Service> matchList;

        if (chooseServiceMap(namespaceId) == null) {
            return 0;
        }

        if (StringUtils.isNotBlank(param)) {
            StringJoiner regex = new StringJoiner(Constants.SERVICE_INFO_SPLITER);
            for (String s : param.split(Constants.SERVICE_INFO_SPLITER)) {
                regex.add(StringUtils.isBlank(s) ? Constants.ANY_PATTERN
                        : Constants.ANY_PATTERN + s + Constants.ANY_PATTERN);
            }
            matchList = searchServices(namespaceId, regex.toString());
        } else {
            matchList = new ArrayList<>(chooseServiceMap(namespaceId).values());
        }

        if (!CollectionUtils.isEmpty(matchList) && hasIpCount) {
            matchList = matchList.stream().filter(s -> !CollectionUtils.isEmpty(s.allIPs()))
                    .collect(Collectors.toList());
        }

        if (StringUtils.isNotBlank(containedInstance)) {

            boolean contained;
            for (int i = 0; i < matchList.size(); i++) {
                Service service = matchList.get(i);
                contained = false;
                List<Instance> instances = service.allIPs();
                for (Instance instance : instances) {
                    if (containedInstance.contains(":")) {
                        if (StringUtils.equals(instance.getIp() + ":" + instance.getPort(), containedInstance)) {
                            contained = true;
                            break;
                        }
                    } else {
                        if (StringUtils.equals(instance.getIp(), containedInstance)) {
                            contained = true;
                            break;
                        }
                    }
                }
                if (!contained) {
                    matchList.remove(i);
                    i--;
                }
            }
        }

        if (pageSize >= matchList.size()) {
            serviceList.addAll(matchList);
            return matchList.size();
        }

        for (int i = 0; i < matchList.size(); i++) {
            if (i < startPage * pageSize) {
                continue;
            }

            serviceList.add(matchList.get(i));

            if (serviceList.size() >= pageSize) {
                break;
            }
        }

        return matchList.size();
    }

    public static class ServiceChecksum {

        public String namespaceId;

        public Map<String, String> serviceName2Checksum = new HashMap<String, String>();

        public ServiceChecksum() {
            this.namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        }

        public ServiceChecksum(String namespaceId) {
            this.namespaceId = namespaceId;
        }

        /**
         * Add service checksum.
         *
         * @param serviceName service name
         * @param checksum    checksum of service
         */
        public void addItem(String serviceName, String checksum) {
            if (StringUtils.isEmpty(serviceName) || StringUtils.isEmpty(checksum)) {
                Loggers.SRV_LOG.warn("[DOMAIN-CHECKSUM] serviceName or checksum is empty,serviceName: {}, checksum: {}",
                        serviceName, checksum);
                return;
            }
            serviceName2Checksum.put(serviceName, checksum);
        }
    }

    private class EmptyServiceAutoClean implements Runnable {

        @Override
        public void run() {

            // Parallel flow opening threshold

            int parallelSize = 100;

            serviceMap.forEach((namespace, stringServiceMap) -> {
                Stream<Map.Entry<String, Service>> stream = null;
                if (stringServiceMap.size() > parallelSize) {
                    stream = stringServiceMap.entrySet().parallelStream();
                } else {
                    stream = stringServiceMap.entrySet().stream();
                }
                stream.filter(entry -> {
                    final String serviceName = entry.getKey();
                    return distroMapper.responsible(serviceName);
                }).forEach(entry -> stringServiceMap.computeIfPresent(entry.getKey(), (serviceName, service) -> {
                    if (service.isEmpty()) {

                        // To avoid violent Service removal, the number of times the Service
                        // experiences Empty is determined by finalizeCnt, and if the specified
                        // value is reached, it is removed

                        if (service.getFinalizeCount() > maxFinalizeCount) {
                            Loggers.SRV_LOG.warn("namespace : {}, [{}] services are automatically cleaned", namespace,
                                    serviceName);
                            try {
                                easyRemoveService(namespace, serviceName);
                            } catch (Exception e) {
                                Loggers.SRV_LOG.error("namespace : {}, [{}] services are automatically clean has "
                                        + "error : {}", namespace, serviceName, e);
                            }
                        }

                        service.setFinalizeCount(service.getFinalizeCount() + 1);

                        Loggers.SRV_LOG
                                .debug("namespace : {}, [{}] The number of times the current service experiences "
                                                + "an empty instance is : {}", namespace, serviceName,
                                        service.getFinalizeCount());
                    } else {
                        service.setFinalizeCount(0);
                    }
                    return service;
                }));
            });
        }
    }

    private class ServiceReporter implements Runnable {

        @Override
        public void run() {
            try {

                Map<String, Set<String>> allServiceNames = getAllServiceNames();

                if (allServiceNames.size() <= 0) {
                    //ignore
                    return;
                }

                for (String namespaceId : allServiceNames.keySet()) {

                    ServiceChecksum checksum = new ServiceChecksum(namespaceId);

                    for (String serviceName : allServiceNames.get(namespaceId)) {
                        if (!distroMapper.responsible(serviceName)) {
                            continue;
                        }

                        Service service = getService(namespaceId, serviceName);

                        if (service == null || service.isEmpty()) {
                            continue;
                        }

                        service.recalculateChecksum();

                        checksum.addItem(serviceName, service.getChecksum());
                    }

                    Message msg = new Message();

                    msg.setData(JacksonUtils.toJson(checksum));

                    Collection<Member> sameSiteServers = memberManager.allMembers();

                    if (sameSiteServers == null || sameSiteServers.size() <= 0) {
                        return;
                    }

                    for (Member server : sameSiteServers) {
                        if (server.getAddress().equals(NetUtils.localServer())) {
                            continue;
                        }
                        synchronizer.send(server.getAddress(), msg);
                    }
                }
            } catch (Exception e) {
                Loggers.SRV_LOG.error("[DOMAIN-STATUS] Exception while sending service status", e);
            } finally {
                GlobalExecutor.scheduleServiceReporter(this, switchDomain.getServiceStatusSynchronizationPeriodMillis(),
                        TimeUnit.MILLISECONDS);
            }
        }
    }

    private static class ServiceKey {

        private String namespaceId;

        private String serviceName;

        private String serverIP;

        private String checksum;

        public String getChecksum() {
            return checksum;
        }

        public String getServerIP() {
            return serverIP;
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getNamespaceId() {
            return namespaceId;
        }

        public ServiceKey(String namespaceId, String serviceName, String serverIP, String checksum) {
            this.namespaceId = namespaceId;
            this.serviceName = serviceName;
            this.serverIP = serverIP;
            this.checksum = checksum;
        }

        @Override
        public String toString() {
            return JacksonUtils.toJson(this);
        }
    }
}
