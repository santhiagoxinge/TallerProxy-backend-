package com.proxy.monitoring.model;

import java.util.List;

public record PagedLogs(
        List<LogEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
