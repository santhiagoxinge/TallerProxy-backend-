package com.proxy.monitoring.proxy;

import com.proxy.monitoring.exception.ServiceFailureException;
import com.proxy.monitoring.model.CallStatus;
import com.proxy.monitoring.model.LogEntry;
import com.proxy.monitoring.model.PagedLogs;
import com.proxy.monitoring.store.InMemoryLogStore;
import com.proxy.monitoring.store.LogStore;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingProxyTest {

    private final LogStore store = new InMemoryLogStore();

    @Test
    void logsSuccessfulCallWithRequiredFields() {
        LoggingProxy<String> proxy = new LoggingProxy<>("inventory", (op, params) -> "done:" + op, store);

        assertEquals("done:checkStock", proxy.execute("checkStock", "a"));

        LogEntry entry = store.find(null, null, null, null, 0, 10).content().get(0);
        assertEquals("inventory", entry.serviceId());
        assertEquals("checkStock", entry.operation());
        assertEquals(CallStatus.SUCCESS, entry.status());
        assertEquals("done:checkStock", entry.response());
        assertNull(entry.error());
        assertNotNull(entry.timestamp());
        assertTrue(entry.durationMs() >= 0);
        UUID.fromString(entry.requestId());
    }

    @Test
    void logsFailureWithSummarizedStackTraceAndRethrows() {
        LoggingProxy<String> proxy = new LoggingProxy<>("payments", (op, params) -> {
            throw new ServiceFailureException("gateway down");
        }, store);

        assertThrows(ServiceFailureException.class, () -> proxy.execute("charge"));

        LogEntry entry = store.find(null, null, null, null, 0, 10).content().get(0);
        assertEquals(CallStatus.ERROR, entry.status());
        assertNull(entry.response());
        assertTrue(entry.error().startsWith("ServiceFailureException: gateway down"));
        assertTrue(entry.error().contains("at "));
    }

    @Test
    void summaryAndFiltersReflectStoredCalls() {
        LoggingProxy<String> ok = new LoggingProxy<>("orders", (op, params) -> "ok", store);
        LoggingProxy<String> bad = new LoggingProxy<>("payments", (op, params) -> {
            throw new ServiceFailureException("boom");
        }, store);
        ok.execute("create");
        ok.execute("create");
        assertThrows(ServiceFailureException.class, () -> bad.execute("charge"));

        var summary = store.summarize(java.util.List.of("inventory", "orders", "payments"));
        assertEquals(3, summary.totalCalls());
        assertEquals(3, summary.services().size());

        PagedLogs errors = store.find("payments", CallStatus.ERROR, null, null, 0, 10);
        assertEquals(1, errors.totalElements());
    }
}
