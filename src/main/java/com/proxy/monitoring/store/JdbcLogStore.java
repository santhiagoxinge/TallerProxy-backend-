package com.proxy.monitoring.store;

import com.proxy.monitoring.model.CallStatus;
import com.proxy.monitoring.model.LogEntry;
import com.proxy.monitoring.model.MetricsSummary;
import com.proxy.monitoring.model.PagedLogs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * PostgreSQL-backed audit trail ({@code proxy_workshop.call_logs}). Keeps the newest {@value #MAX_ROWS} rows.
 */
@Component
public class JdbcLogStore implements LogStore {

    static final int MAX_ROWS = 5000;
    private static final int PRUNE_EVERY = 100;

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final AtomicInteger inserts = new AtomicInteger();

    public JdbcLogStore(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void add(LogEntry entry) {
        jdbc.sql("""
                INSERT INTO proxy_workshop.call_logs
                    (request_id, service_id, operation, duration_ms, status, created_at, params, response, error)
                VALUES (:requestId, :serviceId, :operation, :durationMs, :status, :createdAt,
                        CAST(:params AS jsonb), CAST(:response AS jsonb), :error)
                """)
                .param("requestId", UUID.fromString(entry.requestId()))
                .param("serviceId", entry.serviceId())
                .param("operation", entry.operation())
                .param("durationMs", entry.durationMs())
                .param("status", entry.status().name())
                .param("createdAt", OffsetDateTime.ofInstant(entry.timestamp(), ZoneOffset.UTC))
                .param("params", json.writeValueAsString(entry.params()))
                .param("response", entry.response() == null ? null : json.writeValueAsString(entry.response()))
                .param("error", entry.error())
                .update();

        if (inserts.incrementAndGet() % PRUNE_EVERY == 0) {
            prune();
        }
    }

    @Override
    public PagedLogs find(String service, CallStatus status, Instant from, Instant to, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> filters = new HashMap<>();
        if (service != null) {
            where.append(" AND lower(service_id) = lower(:service)");
            filters.put("service", service);
        }
        if (status != null) {
            where.append(" AND status = :status");
            filters.put("status", status.name());
        }
        if (from != null) {
            where.append(" AND created_at >= :from");
            filters.put("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC));
        }
        if (to != null) {
            where.append(" AND created_at <= :to");
            filters.put("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
        }

        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(0, page);

        long total = jdbc.sql("SELECT count(*) FROM proxy_workshop.call_logs" + where)
                .params(filters)
                .query(Long.class)
                .single();

        List<LogEntry> content = jdbc.sql("""
                SELECT request_id, service_id, operation, duration_ms, status, created_at,
                       params::text AS params, response::text AS response, error
                FROM proxy_workshop.call_logs
                """ + where + " ORDER BY created_at DESC, request_id LIMIT :limit OFFSET :offset")
                .params(filters)
                .param("limit", safeSize)
                .param("offset", (long) safePage * safeSize)
                .query(this::mapRow)
                .list();

        return new PagedLogs(content, safePage, safeSize, total, (int) Math.ceil(total / (double) safeSize));
    }

    @Override
    public MetricsSummary summarize(List<String> knownServices) {
        Map<String, SummaryBuilder.Stats> byService = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT service_id,
                       count(*) AS total,
                       count(*) FILTER (WHERE status = 'ERROR') AS errors,
                       coalesce(sum(duration_ms), 0) AS duration_sum
                FROM proxy_workshop.call_logs
                GROUP BY service_id
                """)
                .query((rs, row) -> byService.put(rs.getString("service_id"),
                        new SummaryBuilder.Stats(rs.getLong("total"), rs.getLong("errors"), rs.getLong("duration_sum"))))
                .list();
        return SummaryBuilder.build(byService, knownServices);
    }

    private void prune() {
        jdbc.sql("""
                DELETE FROM proxy_workshop.call_logs
                WHERE request_id IN (
                    SELECT request_id FROM proxy_workshop.call_logs
                    ORDER BY created_at DESC, request_id
                    OFFSET :keep
                )
                """)
                .param("keep", MAX_ROWS)
                .update();
    }

    private LogEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        String params = rs.getString("params");
        String response = rs.getString("response");
        return new LogEntry(
                rs.getString("request_id"),
                rs.getString("service_id"),
                rs.getString("operation"),
                rs.getLong("duration_ms"),
                CallStatus.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                params == null ? List.of() : json.readValue(params, List.class),
                response == null ? null : json.readValue(response, Object.class),
                rs.getString("error"));
    }
}
