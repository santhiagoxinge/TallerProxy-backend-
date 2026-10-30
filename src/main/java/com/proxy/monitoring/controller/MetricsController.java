package com.proxy.monitoring.controller;

import com.proxy.monitoring.exception.InvalidParameterException;
import com.proxy.monitoring.model.CallStatus;
import com.proxy.monitoring.model.MetricsSummary;
import com.proxy.monitoring.model.PagedLogs;
import com.proxy.monitoring.service.LoadSimulator;
import com.proxy.monitoring.service.ServiceRegistry;
import com.proxy.monitoring.store.LogStore;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private static final int SIMULATED_CALLS = 50;

    private final LogStore store;
    private final ServiceRegistry registry;
    private final LoadSimulator simulator;

    public MetricsController(LogStore store, ServiceRegistry registry, LoadSimulator simulator) {
        this.store = store;
        this.registry = registry;
        this.simulator = simulator;
    }

    @GetMapping("/summary")
    public MetricsSummary summary() {
        return store.summarize(registry.ids());
    }

    @GetMapping("/logs")
    public PagedLogs logs(@RequestParam(required = false) String service,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String from,
                          @RequestParam(required = false) String to,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        return store.find(blankToNull(service), parseStatus(status), parseInstant("from", from),
                parseInstant("to", to), page, size);
    }

    @PostMapping("/simulate-load")
    public Map<String, Object> simulateLoad() {
        return simulator.run(SIMULATED_CALLS);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private CallStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CallStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidParameterException("status must be SUCCESS or ERROR");
        }
    }

    private Instant parseInstant(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new InvalidParameterException(name + " must be an ISO-8601 instant, e.g. 2026-10-30T14:00:00Z");
        }
    }
}
