package com.proxy.monitoring.exception;

public class UnknownOperationException extends RuntimeException {

    public UnknownOperationException(String service, String operation) {
        super("Service '" + service + "' has no operation '" + operation + "'");
    }
}
