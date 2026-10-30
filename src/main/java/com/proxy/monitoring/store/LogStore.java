package com.proxy.monitoring.store;

import com.proxy.monitoring.model.CallStatus;
import com.proxy.monitoring.model.LogEntry;
import com.proxy.monitoring.model.MetricsSummary;
import com.proxy.monitoring.model.PagedLogs;
import com.proxy.monitoring.model.ServiceSummary;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

/**
 * Bounded in-memory log storage. Newest entries are kept first.
 */
@Component
public class LogStore {

    static final int MAX_ENTRIES = 5000;

    private final Deque<LogEntry> entries = new ArrayDeque<>();

    public synchronized void add(LogEntry entry) {
        entries.addFirst(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    public synchronized void clear() {
        entries.clear();
    }

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

    public synchronized MetricsSummary summarize(List<String> knownServices) {
        Map<String, List<LogEntry>> byService = new TreeMap<>();
        knownServices.forEach(id -> byService.put(id, List.of()));
        entries.stream()
                .collect(java.util.stream.Collectors.groupingBy(LogEntry::serviceId))
                .forEach(byService::put);

        List<ServiceSummary> services = byService.entrySet().stream()
                .map(e -> summarizeService(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ServiceSummary::serviceId))
                .toList();

        long total = entries.size();
        long errors = entries.stream().filter(e -> e.status() == CallStatus.ERROR).count();
        double average = entries.stream().mapToLong(LogEntry::durationMs).average().orElse(0);

        return new MetricsSummary(total, percentage(errors, total), round(average), services);
    }

    private ServiceSummary summarizeService(String serviceId, List<LogEntry> logs) {
        long total = logs.size();
        long errors = logs.stream().filter(e -> e.status() == CallStatus.ERROR).count();
        double average = logs.stream().mapToLong(LogEntry::durationMs).average().orElse(0);
        return new ServiceSummary(serviceId, total, total - errors, errors, percentage(errors, total), round(average));
    }

    private double percentage(long part, long total) {
        return total == 0 ? 0 : round(part * 100.0 / total);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
