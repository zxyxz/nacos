package com.alibaba.nacos.naming.core;

/**
 * @author xuyang
 * @version ServiceSubInfo.java, v 0.1 2021年03月19日 14:10 xuyang Exp $
 */
public class ServiceSubInfo {

    private String  instanceId;

    private String  dataId;

    private String  processId;

    private String  hostIp;

    private String  appName;


    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getHostIp() {
        return hostIp;
    }

    public void setHostIp(String hostIp) {
        this.hostIp = hostIp;
    }


    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }
}
