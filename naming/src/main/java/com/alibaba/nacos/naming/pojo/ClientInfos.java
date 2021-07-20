package com.alibaba.nacos.naming.pojo;


import java.util.Set;

/**
 * @author xuyang
 * @version ClientInfo.java, v 0.1 2021年07月20日 15:08 xuyang Exp $
 */
public class ClientInfos {

    private String serviceName;

    private Set<PushClient> pushClients;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Set<PushClient> getPushClients() {
        return pushClients;
    }

    public void setPushClients(Set<PushClient> pushClients) {
        this.pushClients = pushClients;
    }
}
