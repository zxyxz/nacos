package com.alibaba.nacos.naming.pojo;


import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;

/**
 * @author xuyang
 * @version PushClient.java, v 0.1 2021年07月20日 17:56 xuyang Exp $
 */
public class PushClient {

    private String namespaceId;

    private String serviceName;

    private String clusters;

    private String agent;

    private String tenant;

    private String app;

    private InetSocketAddress socketAddr;

    private Map<String, String[]> params;

    public String getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getClusters() {
        return clusters;
    }

    public void setClusters(String clusters) {
        this.clusters = clusters;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public InetSocketAddress getSocketAddr() {
        return socketAddr;
    }

    public void setSocketAddr(InetSocketAddress socketAddr) {
        this.socketAddr = socketAddr;
    }

    public Map<String, String[]> getParams() {
        return params;
    }

    public void setParams(Map<String, String[]> params) {
        this.params = params;
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, clusters, socketAddr);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PushClient)) {
            return false;
        }

        PushClient other = (PushClient) obj;

        return serviceName.equals(other.serviceName) && clusters.equals(other.clusters) && socketAddr
            .equals(other.socketAddr);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("serviceName: ").append(serviceName).append(", clusters: ").append(clusters).append(", address: ")
            .append(socketAddr).append(", agent: ").append(agent);
        return sb.toString();
    }


}
