package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.common.util.HttpMethod;
import com.alibaba.nacos.naming.core.ServerStatus;
import com.alibaba.nacos.naming.misc.Switch;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author nkorange
 */
public class TrafficReviseFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;


        // if server is UP:
        if (ServerStatus.UP.name().equals(Switch.getServerStatus())) {
            filterChain.doFilter(req, resp);
            return;
        }

        // requests from peer server should be let pass:
        String agent = req.getHeader("Client-Version");
        if (StringUtils.isBlank(agent)) {
            agent = req.getHeader("User-Agent");
        }

        if (StringUtils.startsWith(agent, UtilsAndCommons.NACOS_SERVER_HEADER)) {
            filterChain.doFilter(req, resp);
            return;
        }

        // write operation should be let pass in WRITE_ONLY status:
        if (ServerStatus.WRITE_ONLY.name().equals(Switch.getServerStatus()) && !HttpMethod.GET.equals(req.getMethod())) {
            filterChain.doFilter(req, resp);
            return;
        }

        // read operation should be let pass in READY_ONLY status:
        if (ServerStatus.READY_ONLY.name().equals(Switch.getServerStatus()) && HttpMethod.GET.equals(req.getMethod())) {
            filterChain.doFilter(req, resp);
            return;
        }

        resp.getWriter().write("server is " + Switch.getServerStatus() + " now, please try again later!");
        resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }
}
