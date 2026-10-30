package com.proxy.monitoring.model;

public record ServiceSummary(
        String serviceId,
        long totalCalls,
        long successCalls,
        long errorCalls,
        double errorRate,
        double averageDurationMs) {
}
