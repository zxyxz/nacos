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

import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * ServiceV2 operation controller.
 *
 * @author bangbang2333
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT)
public class ServiceV2Controller {

    @Autowired
    private ServiceManager serviceManager;

    @GetMapping(value = "/services/list")
    public Map getServicesList(@RequestParam(value = "instanceId") String instanceId,
                               @RequestParam(value = "pageNo") int pageNo,
                               @RequestParam(value = "pageSize") int pageSize,
                               @RequestParam(value = "serviceName", required = false) String serviceName,
                               @RequestParam(value = "namespaceId", required = false) String namespaceId,
                               @RequestParam(value = "groupName", required = false) String groupName,
                               @RequestParam(value = "appName", required = false) String appName) throws Exception {
        HashMap<String, Object> result = serviceManager.getServicesList(instanceId, pageNo, pageSize, serviceName, appName, groupName, namespaceId);
        return result;
    }

    @GetMapping(value = "/apps/list")
    public Map getAppsList(@RequestParam(value = "instanceId") String instanceId,
                           @RequestParam(value = "groupName", required = false) String groupName,
                           @RequestParam(value = "namespaceId", required = false) String namespaceId) throws Exception {
        HashMap<String, Object> result = serviceManager.getAppsList(instanceId, groupName, namespaceId);
        return result;
    }

    @GetMapping(value = "/services/client/list")
    public Map getServicesClientList(@RequestParam(value = "instanceId") String instanceId,
                                     @RequestParam(value = "appName") String appName,
                                     @RequestParam(value = "groupName", required = false) String groupName,
                                     @RequestParam(value = "namespaceId", required = false) String namespaceId) throws Exception {
        HashMap<String, Object> result = serviceManager.getServicesClientList(instanceId, appName, groupName, namespaceId);
        return result;
    }

    @GetMapping(value = "/services/server/list")
    public Map getServicesServerList(@RequestParam(value = "instanceId") String instanceId,
                                     @RequestParam(value = "appName") String appName,
                                     @RequestParam(value = "groupName", required = false) String groupName,
                                     @RequestParam(value = "namespaceId", required = false) String namespaceId) throws Exception {
        HashMap<String, Object> result = serviceManager.getServicesServerList(instanceId, appName, groupName, namespaceId);
        return result;
    }

    @GetMapping(value = "/services/serverpub/detail/list")
    public Map getServicesServerPubDetailList(@RequestParam(value = "instanceId") String instanceId,
                                              @RequestParam(value = "serviceName") String serviceName,
                                              @RequestParam(value = "groupName", required = false) String groupName,
                                              @RequestParam(value = "namespaceId", required = false) String namespaceId) throws Exception {
        HashMap<String, Object> result = serviceManager.getServicesServerPubDetailList(instanceId, serviceName, groupName, namespaceId);
        return result;
    }

    @GetMapping(value = "/services/serversub/detail/list")
    public Map getServicesServerSubDetailList(@RequestParam(value = "instanceId") String instanceId,
                                              @RequestParam(value = "serviceName") String serviceName,
                                              @RequestParam(value = "groupName", required = false) String groupName,
                                              @RequestParam(value = "namespaceId", required = false) String namespaceId) throws Exception {
        HashMap<String, Object> result = serviceManager.getServicesServerSubDetailList(instanceId, serviceName, groupName, namespaceId);
        return result;
    }

}
