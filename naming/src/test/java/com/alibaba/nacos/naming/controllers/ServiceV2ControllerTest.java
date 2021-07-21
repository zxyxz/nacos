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
package com.alibaba.nacos.naming.controllers;

import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
public class ServiceV2ControllerTest extends BaseTest {

    private MockMvc mockmvc;

    @InjectMocks
    private ServiceV2Controller serviceV2Controller;

    @Before
    public void before() {
        mockmvc = MockMvcBuilders.standaloneSetup(serviceV2Controller).build();
    }

    @Test
    public void testGetServicesList() throws Exception {

        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/services/list")
            .param("instanceId", "nacosCluster1")
            .param("pageNo", "1")
            .param("pageSize", "2")
            .param("serviceName", "nacos")
            .param("namespaceId", "sofamesh")
            .param("groupName", "SOFA")
            .param("appName", "crpc-server4");
        MockHttpServletResponse response = mockmvc.perform(builder)
            .andReturn()
            .getResponse();
        String content = response.getContentAsString();
        int status = response.getStatus();
        System.out.println(content);
        Assert.assertEquals(200, status);
    }

    @Test
    public void testGetAppsList()  throws Exception {

        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/apps/list")
            .param("instanceId", "nacosCluster1")
            .param("namespaceId", "sofamesh")
            .param("groupName", "SOFA");
        MockHttpServletResponse response = mockmvc.perform(builder)
            .andReturn()
            .getResponse();
        String content = response.getContentAsString();
        int status = response.getStatus();
        System.out.println(content);
        Assert.assertEquals(200, status);

    }

    @Test
    public void testGetServicesClientList()  throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/services/client/list")
            .param("instanceId", "nacosCluster1")
            .param("namespaceId", "sofamesh")
            .param("groupName", "SOFA")
            .param("appName", "crpc-server4");
        MockHttpServletResponse response = mockmvc.perform(builder)
            .andReturn()
            .getResponse();
        String content = response.getContentAsString();
        int status = response.getStatus();
        System.out.println(content);
        Assert.assertEquals(200, status);

    }

    @Test
    public void testGetServicesServerList()  throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/services/server/list")
            .param("instanceId", "nacosCluster1")
            .param("namespaceId", "sofamesh")
            .param("groupName", "SOFA")
            .param("appName", "crpc-server4");
        MockHttpServletResponse response = mockmvc.perform(builder)
            .andReturn()
            .getResponse();
        String content = response.getContentAsString();
        int status = response.getStatus();
        System.out.println(content);
        Assert.assertEquals(200, status);

    }

    @Test
    public void testGetServicesServerPubDetailList()  throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/services/serverpub/detail/list")
            .param("instanceId", "nacosCluster1")
            .param("serviceName", "nacos")
            .param("namespaceId", "sofamesh")
            .param("groupName", "SOFA");
        MockHttpServletResponse response = mockmvc.perform(builder)
            .andReturn()
            .getResponse();
        String content = response.getContentAsString();
        int status = response.getStatus();
        System.out.println(content);
        Assert.assertEquals(200, status);

    }

    @Test
    public void testGetServicesServerSubDetailList()  throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
            .get(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/services/serversub/detail/list")
            .param("instanceId", "nacosCluster1")
            .param("serviceName", "nacos")
            .param("namespaceId", "sofamesh")
            .param("groupName", "SOFA");
        MockHttpServletResponse response = mockmvc.perform(builder)
            .andReturn()
            .getResponse();
        String content = response.getContentAsString();
        int status = response.getStatus();
        System.out.println(content);
        Assert.assertEquals(200, status);
    }
}
