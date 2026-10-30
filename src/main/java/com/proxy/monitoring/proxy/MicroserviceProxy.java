package com.proxy.monitoring.proxy;

/**
 * Common contract shared by the real microservices and the proxies that wrap them.
 */
public interface MicroserviceProxy<T> {

    T execute(String operation, Object... params);
}
