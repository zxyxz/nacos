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

import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeerSet;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.PushService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.Mockito.when;


public class ServiceManager2Test extends BaseTest {

    @Mock
    SwitchDomain switchDomain;
    @Mock
    DistroMapper distroMapper;
    @Mock
    ServerMemberManager memberManager;
    @Mock
    RaftPeerSet raftPeerSet;

    @Spy
    private ServiceManager serviceManager=new ServiceManager(switchDomain, distroMapper, memberManager, pushService, raftPeerSet);

    String namespaceId = "sofamesh";
    String groupName = "SOFA";
    String clusterName = "nacosCluster1";
    String serviceName = groupName + "@@service";
    String appName = "app1";
    String metaData = "&appName=" + appName + "&";
    String clientIp = "127.0.0.1";

    @Before
    public void before() {
        super.before();
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setName(serviceName);
        Cluster cluster = new Cluster();
        Set<Instance> instances = new HashSet<>();
        Instance instance = new Instance();
        instance.setClusterName(clusterName);
        instance.setIp(clientIp);
        instance.setServiceName(serviceName);
        instance.getMetadata().put("pubData", metaData);
        instances.add(instance);
        cluster.setPersistentInstances(instances);
        service.getClusterMap().put(clusterName, cluster);
        serviceManager.putService(service);
        ReflectionTestUtils.setField(serviceManager, "pushService", pushService);
        ConcurrentHashMap<String, ConcurrentMap<String, PushService.PushClient>> clientMap = new ConcurrentHashMap<>();
        String serviceKey = UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName);
        ConcurrentMap<String, PushService.PushClient> clients = new ConcurrentHashMap<>();
        try {
            PushService.PushClient pushClient = (PushService.PushClient) Class.forName("com.alibaba.nacos.naming.push.PushService$PushClient")
                .getDeclaredConstructors()[0].newInstance(new PushService());
            pushClient.setApp(appName);
            pushClient.setClusters(clusterName);
            pushClient.setServiceName(serviceName);
            pushClient.setNamespaceId(namespaceId);
            InetSocketAddress socketAddr = new InetSocketAddress(clientIp,123);
            ReflectionTestUtils.setField(pushClient, "socketAddr", socketAddr);
            clients.put(serviceKey,pushClient);
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException | InvocationTargetException e) {
            e.printStackTrace();
        }
        clientMap.put(serviceKey, clients);
        when(pushService.getClientMap()).thenReturn(clientMap);
    }

    @Test
    public void testGetServicesList() {
        HashMap<String, Object> map = serviceManager.getServicesList(clusterName, 1, 2, serviceName, appName, groupName, namespaceId);
        List<Map<String, Object>> list = (List<Map<String, Object>>) map.get("list");
        Assert.assertTrue(list.size() > 0);
    }

    @Test
    public void testGetAppsList() {
        HashMap<String, Object> map = serviceManager.getAppsList(clusterName, groupName, namespaceId);
        Set<String> list = (Set<String>) map.get("apps");
        Assert.assertTrue(list.size() > 0);
    }

    @Test
    public void testGetServicesClientList() {
        HashMap<String, Object> map = serviceManager.getServicesClientList(clusterName, appName, groupName, namespaceId);
        Set<String> serverSet  = (Set<String>) map.get("servers");
        Assert.assertTrue(serverSet.size() > 0);
    }

    @Test
    public void testGetServicesServerList() {
        HashMap<String, Object> map = serviceManager.getServicesServerList(clusterName, appName, groupName, namespaceId);
        Set<String> serverSet  = (Set<String>) map.get("servers");
        Assert.assertTrue(serverSet.size() > 0);
    }

    @Test
    public void testGetServicesServerPubDetailList() {
        HashMap<String, Object> map = serviceManager.getServicesServerPubDetailList(clusterName, serviceName, groupName, namespaceId);
        Set<String> details = (Set<String>) map.get("details");
        Assert.assertTrue(details.size() > 0);
    }

    @Test
    public void testGetServicesServerSubDetailList() {
        HashMap<String, Object> map = serviceManager.getServicesServerSubDetailList(clusterName, serviceName, groupName, namespaceId);
        List<Map<String, Object>> list = (List<Map<String, Object>>) map.get("details");
        Assert.assertTrue(list.size() > 0);
    }
}
