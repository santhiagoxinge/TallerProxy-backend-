package com.proxy.monitoring.proxy;

import com.proxy.monitoring.model.CallStatus;
import com.proxy.monitoring.model.LogEntry;
import com.proxy.monitoring.store.LogStore;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps any {@link MicroserviceProxy}: logs the start of each call, then its duration and
 * result, or a summarized stack trace when the call fails. The original exception is rethrown.
 */
public class LoggingProxy<T> implements MicroserviceProxy<T> {

    private static final Logger log = LoggerFactory.getLogger(LoggingProxy.class);
    private static final int STACK_FRAMES = 3;

    private final String serviceId;
    private final MicroserviceProxy<T> delegate;
    private final LogStore store;

    public LoggingProxy(String serviceId, MicroserviceProxy<T> delegate, LogStore store) {
        this.serviceId = serviceId;
        this.delegate = delegate;
        this.store = store;
    }

    public String serviceId() {
        return serviceId;
    }

    @Override
    public T execute(String operation, Object... params) {
        String requestId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        long start = System.nanoTime();
        log.info("[{}] START {}.{}", requestId, serviceId, operation);

        try {
            T result = delegate.execute(operation, params);
            long duration = elapsedMs(start);
            store.add(new LogEntry(requestId, serviceId, operation, duration, CallStatus.SUCCESS,
                    timestamp, Arrays.asList(params), result, null));
            log.info("[{}] SUCCESS {}.{} in {}ms", requestId, serviceId, operation, duration);
            return result;
        } catch (RuntimeException ex) {
            long duration = elapsedMs(start);
            store.add(new LogEntry(requestId, serviceId, operation, duration, CallStatus.ERROR,
                    timestamp, Arrays.asList(params), null, summarize(ex)));
            log.warn("[{}] ERROR {}.{} in {}ms: {}", requestId, serviceId, operation, duration, ex.getMessage());
            throw ex;
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    static String summarize(Throwable ex) {
        String frames = Arrays.stream(ex.getStackTrace())
                .limit(STACK_FRAMES)
                .map(f -> "at " + f.getClassName() + "." + f.getMethodName()
                        + "(" + f.getFileName() + ":" + f.getLineNumber() + ")")
                .collect(Collectors.joining(" | "));
        return ex.getClass().getSimpleName() + ": " + ex.getMessage() + " | " + frames;
    }
}
