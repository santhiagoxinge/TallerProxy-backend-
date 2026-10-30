package com.proxy.monitoring.service;

import com.proxy.monitoring.exception.ResourceNotFoundException;
import com.proxy.monitoring.exception.UnknownOperationException;
import com.proxy.monitoring.proxy.MicroserviceProxy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.proxy.monitoring.service.ServiceSupport.body;
import static com.proxy.monitoring.service.ServiceSupport.decimal;
import static com.proxy.monitoring.service.ServiceSupport.integer;
import static com.proxy.monitoring.service.ServiceSupport.simulateLatency;
import static com.proxy.monitoring.service.ServiceSupport.text;

/**
 * Real order microservice. Operations: create, get, cancel, list.
 */
public class OrderService implements MicroserviceProxy<Map<String, Object>> {

    private final Map<String, Map<String, Object>> orders = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(1000);

    @Override
    public Map<String, Object> execute(String operation, Object... params) {
        simulateLatency(30, 160);
        Map<String, Object> body = body(params);
        return switch (operation) {
            case "create" -> create(text(body, "customerId"), text(body, "sku"),
                    integer(body, "quantity"), decimal(body, "unitPrice"));
            case "get" -> find(text(body, "orderId"));
            case "cancel" -> cancel(text(body, "orderId"));
            case "list" -> Map.of("orders", List.copyOf(orders.values()));
            default -> throw new UnknownOperationException("orders", operation);
        };
    }

    private Map<String, Object> create(String customerId, String sku, int quantity, double unitPrice) {
        String orderId = "ORD-" + sequence.incrementAndGet();
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", orderId);
        order.put("customerId", customerId);
        order.put("sku", sku);
        order.put("quantity", quantity);
        order.put("total", Math.round(quantity * unitPrice * 100.0) / 100.0);
        order.put("status", "CREATED");
        order.put("createdAt", Instant.now().toString());
        orders.put(orderId, order);
        return order;
    }

    private Map<String, Object> find(String orderId) {
        Map<String, Object> order = orders.get(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        return order;
    }

    private Map<String, Object> cancel(String orderId) {
        Map<String, Object> order = find(orderId);
        order.put("status", "CANCELLED");
        return order;
    }
}
