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

import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.misc.Switch;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/operator")
public class OperatorController {

    @RequestMapping(value = "/status")
    public String getStatus(HttpServletRequest request) throws Exception {
        return Switch.getServerStatus();
    }

    @RequestMapping(value = "/status", method = RequestMethod.PUT)
    public String updateStatus(HttpServletRequest request) throws Exception {
        String serverStatus = WebUtils.required(request, "status");
        Switch.setServerStatus(serverStatus);
        return "ok";
    }
}
