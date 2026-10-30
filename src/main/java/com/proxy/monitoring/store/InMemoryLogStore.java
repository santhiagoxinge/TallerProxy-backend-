package com.proxy.monitoring.store;

import com.proxy.monitoring.model.CallStatus;
import com.proxy.monitoring.model.LogEntry;
import com.proxy.monitoring.model.MetricsSummary;
import com.proxy.monitoring.model.PagedLogs;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded in-memory implementation. It is not wired into the application (logs live in PostgreSQL);
 * it keeps the proxy unit tests independent of a database.
 */
public class InMemoryLogStore implements LogStore {

    static final int MAX_ENTRIES = 5000;

    private final Deque<LogEntry> entries = new ArrayDeque<>();

    @Override
    public synchronized void add(LogEntry entry) {
        entries.addFirst(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    @Override
    public synchronized PagedLogs find(String service, CallStatus status, Instant from, Instant to,
                                       int page, int size) {
        List<LogEntry> filtered = entries.stream()
                .filter(e -> service == null || e.serviceId().equalsIgnoreCase(service))
                .filter(e -> status == null || e.status() == status)
                .filter(e -> from == null || !e.timestamp().isBefore(from))
                .filter(e -> to == null || !e.timestamp().isAfter(to))
                .toList();

        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(0, page);
        int totalPages = (int) Math.ceil(filtered.size() / (double) safeSize);
        int start = Math.min(safePage * safeSize, filtered.size());
        int end = Math.min(start + safeSize, filtered.size());

        return new PagedLogs(filtered.subList(start, end), safePage, safeSize, filtered.size(), totalPages);
    }

    @Override
    public synchronized MetricsSummary summarize(List<String> knownServices) {
        Map<String, SummaryBuilder.Stats> byService = new HashMap<>();
        for (LogEntry entry : entries) {
            byService.merge(entry.serviceId(),
                    new SummaryBuilder.Stats(1, entry.status() == CallStatus.ERROR ? 1 : 0, entry.durationMs()),
                    (a, b) -> new SummaryBuilder.Stats(a.total() + b.total(), a.errors() + b.errors(),
                            a.durationSumMs() + b.durationSumMs()));
        }
        return SummaryBuilder.build(byService, knownServices);
    }
}
