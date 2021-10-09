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
import com.alibaba.nacos.naming.push.PushService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.HashMap;

public class ServiceManager2Test extends BaseTest {

    @Mock
    SwitchDomain switchDomain;
    @Mock
    DistroMapper distroMapper;
    @Mock
    ServerMemberManager memberManager;
    @Mock
    PushService pushService;
    @Mock
    RaftPeerSet raftPeerSet;

    private ServiceManager serviceManager;

    @Before
    public void before() {
        super.before();
        serviceManager = new ServiceManager(switchDomain, distroMapper, memberManager, pushService, raftPeerSet);
    }

    @Test
    public void testGetServicesList() {
        HashMap<String, Object> list = serviceManager.getServicesList("nacosCluster1", 1, 2, "group@@nacos", "crpc-server4", "SOFA", "sofamesh");
        System.out.println(list);
    }

    @Test
    public void testGetAppsList() {
        HashMap<String, Object> appsList = serviceManager.getAppsList("nacosCluster1", "SOFA", "sofamesh");
        System.out.println(appsList);

    }

    @Test
    public void testGetServicesClientList() {
        HashMap<String, Object> servicesClientList = serviceManager.getServicesClientList("nacosCluster1", "crpc-server4", "SOFA", "sofamesh");
        System.out.println(servicesClientList);
    }

    @Test
    public void testGetServicesServerList() {
        HashMap<String, Object> servicesServerList = serviceManager.getServicesServerList("nacosCluster1", "crpc-server4", "SOFA", "sofamesh");
        System.out.println(servicesServerList);
    }

    @Test
    public void testGetServicesServerPubDetailList() {
        HashMap<String, Object> servicesServerPubDetailList = serviceManager.getServicesServerPubDetailList("nacosCluster1", "group@@nacos", "SOFA", "sofamesh");
        System.out.println(servicesServerPubDetailList);
    }

    @Test
    public void testGetServicesServerSubDetailList() {
        HashMap<String, Object> servicesServerSubDetailList = serviceManager.getServicesServerSubDetailList("nacosCluster1", "group@@nacos", "SOFA", "sofamesh");
        System.out.println(servicesServerSubDetailList);
    }
}
