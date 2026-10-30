package com.proxy.monitoring.exception;

/**
 * Failure raised by a service while processing a valid request (e.g. the simulated payment gateway outage).
 */
public class ServiceFailureException extends RuntimeException {

    public ServiceFailureException(String message) {
        super(message);
    }
}
