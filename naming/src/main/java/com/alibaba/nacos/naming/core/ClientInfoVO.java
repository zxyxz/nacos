package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.naming.pojo.ClientInfos;

import java.util.ArrayList;
import java.util.List;

/**
 * @author xuyang
 * @version Subscribers.java, v 0.1 2021年07月20日 14:56 xuyang Exp $
 */
public class ClientInfoVO {
    private List<ClientInfos> clientInfos = new ArrayList<>();

    public List<ClientInfos> getClientInfos() {
        return clientInfos;
    }

    public void setClientInfos(List<ClientInfos> clientInfos) {
        this.clientInfos = clientInfos;
    }
}
