package com.alibaba.nacos.naming.core;

import java.util.List;

/**
 * @author xuyang
 * @version ServiceInfoModel.java, v 0.1 2021年03月19日 14:19 xuyang Exp $
 */
public class ServiceInfoModel {

    private String serviceName;

    private List<ServicePubInfo> pubInfos;

    private List<ServiceSubInfo> subInfos;

    public List<ServicePubInfo> getPubInfos() {
        return pubInfos;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setPubInfos(List<ServicePubInfo> pubInfos) {
        this.pubInfos = pubInfos;
    }

    public List<ServiceSubInfo> getSubInfos() {
        return subInfos;
    }

    public void setSubInfos(List<ServiceSubInfo> subInfos) {
        this.subInfos = subInfos;
    }
}
