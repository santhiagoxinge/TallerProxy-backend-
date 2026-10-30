package com.proxy.monitoring.model;

import java.time.Instant;
import java.util.List;

/**
 * One audited call. {@code response} is null on errors, {@code error} is null on success.
 */
public record LogEntry(
        String requestId,
        String serviceId,
        String operation,
        long durationMs,
        CallStatus status,
        Instant timestamp,
        List<Object> params,
        Object response,
        String error) {
}
