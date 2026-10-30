package com.proxy.monitoring.store;

import com.proxy.monitoring.model.CallStatus;
import com.proxy.monitoring.model.LogEntry;
import com.proxy.monitoring.model.MetricsSummary;
import com.proxy.monitoring.model.PagedLogs;

import java.time.Instant;
import java.util.List;

/**
 * Storage for the audit trail written by the logging proxy.
 */
public interface LogStore {

    void add(LogEntry entry);

    /** Newest entries first. Null filters are ignored. */
    PagedLogs find(String service, CallStatus status, Instant from, Instant to, int page, int size);

    MetricsSummary summarize(List<String> knownServices);
}
