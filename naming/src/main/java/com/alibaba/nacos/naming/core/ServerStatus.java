package com.alibaba.nacos.naming.core;

/**
 * @author nkorange
 */
public enum ServerStatus {
    /**
     * server is up and ready for request
     */
    UP,
    /**
     * server is out of service, something abnormal happened
     */
    DOWN,
    /**
     * server is preparing itself for request, usually 'UP' is the next status
     */
    STARTING,
    /**
     * server is manually paused
     */
    PAUSED,
    /**
     * only write operation is permitted.
     */
    WRITE_ONLY,
    /**
     * only read operation is permitted.
     */
    READY_ONLY
}
