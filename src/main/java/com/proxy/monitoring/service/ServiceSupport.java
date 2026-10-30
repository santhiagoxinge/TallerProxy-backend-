package com.proxy.monitoring.service;

import com.proxy.monitoring.exception.InvalidParameterException;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Helpers shared by the real services: parameter extraction and simulated latency.
 */
final class ServiceSupport {

    private ServiceSupport() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> body(Object... params) {
        if (params == null || params.length == 0 || !(params[0] instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return (Map<String, Object>) map;
    }

    static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new InvalidParameterException("Missing required parameter: " + key);
        }
        return value.toString();
    }

    static int integer(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(body, key));
        } catch (NumberFormatException ex) {
            throw new InvalidParameterException("Parameter " + key + " must be an integer");
        }
    }

    static double decimal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(text(body, key));
        } catch (NumberFormatException ex) {
            throw new InvalidParameterException("Parameter " + key + " must be a number");
        }
    }

    static void simulateLatency(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs + 1));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
