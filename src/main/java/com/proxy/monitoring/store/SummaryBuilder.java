package com.proxy.monitoring.store;

import com.proxy.monitoring.model.MetricsSummary;
import com.proxy.monitoring.model.ServiceSummary;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns raw per-service counters into the summary returned by the API, so every
 * {@link LogStore} implementation reports identical numbers.
 */
final class SummaryBuilder {

    record Stats(long total, long errors, long durationSumMs) {
    }

    private SummaryBuilder() {
    }

    static MetricsSummary build(Map<String, Stats> byService, List<String> knownServices) {
        Map<String, Stats> all = new TreeMap<>(byService);
        knownServices.forEach(id -> all.putIfAbsent(id, new Stats(0, 0, 0)));

        List<ServiceSummary> services = all.entrySet().stream()
                .map(e -> toSummary(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ServiceSummary::serviceId))
                .toList();

        long total = all.values().stream().mapToLong(Stats::total).sum();
        long errors = all.values().stream().mapToLong(Stats::errors).sum();
        long durations = all.values().stream().mapToLong(Stats::durationSumMs).sum();

        return new MetricsSummary(total, percentage(errors, total), average(durations, total), services);
    }

    private static ServiceSummary toSummary(String serviceId, Stats stats) {
        return new ServiceSummary(serviceId, stats.total(), stats.total() - stats.errors(), stats.errors(),
                percentage(stats.errors(), stats.total()), average(stats.durationSumMs(), stats.total()));
    }

    private static double percentage(long part, long total) {
        return total == 0 ? 0 : round(part * 100.0 / total);
    }

    private static double average(long sum, long count) {
        return count == 0 ? 0 : round(sum / (double) count);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
