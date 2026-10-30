package com.proxy.monitoring.model;

import java.util.List;

public record MetricsSummary(
        long totalCalls,
        double errorRate,
        double averageDurationMs,
        List<ServiceSummary> services) {
}
