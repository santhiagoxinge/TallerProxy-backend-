package com.proxy.monitoring.service;

import com.proxy.monitoring.exception.InvalidParameterException;
import com.proxy.monitoring.exception.ResourceNotFoundException;
import com.proxy.monitoring.exception.UnknownOperationException;
import com.proxy.monitoring.proxy.MicroserviceProxy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.proxy.monitoring.service.ServiceSupport.body;
import static com.proxy.monitoring.service.ServiceSupport.integer;
import static com.proxy.monitoring.service.ServiceSupport.simulateLatency;
import static com.proxy.monitoring.service.ServiceSupport.text;

/**
 * Real inventory microservice. Operations: listProducts, checkStock, reserve, restock.
 */
public class InventoryService implements MicroserviceProxy<Map<String, Object>> {

    private final Map<String, Integer> stock = new ConcurrentHashMap<>(Map.of(
            "SKU-LAPTOP", 25,
            "SKU-MOUSE", 140,
            "SKU-KEYBOARD", 80,
            "SKU-MONITOR", 40,
            "SKU-HEADSET", 60));

    @Override
    public Map<String, Object> execute(String operation, Object... params) {
        simulateLatency(20, 120);
        Map<String, Object> body = body(params);
        return switch (operation) {
            case "listProducts" -> Map.of("products", Map.copyOf(stock));
            case "checkStock" -> checkStock(text(body, "sku"));
            case "reserve" -> reserve(text(body, "sku"), integer(body, "quantity"));
            case "restock" -> restock(text(body, "sku"), integer(body, "quantity"));
            default -> throw new UnknownOperationException("inventory", operation);
        };
    }

    private Map<String, Object> checkStock(String sku) {
        return Map.of("sku", sku, "available", currentStock(sku));
    }

    private Map<String, Object> reserve(String sku, int quantity) {
        requirePositive(quantity);
        int available = currentStock(sku);
        if (quantity > available) {
            throw new InvalidParameterException("Insufficient stock for " + sku
                    + ": requested " + quantity + ", available " + available);
        }
        int remaining = stock.merge(sku, -quantity, Integer::sum);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sku", sku);
        result.put("reserved", quantity);
        result.put("remaining", remaining);
        return result;
    }

    private Map<String, Object> restock(String sku, int quantity) {
        requirePositive(quantity);
        currentStock(sku);
        int updated = stock.merge(sku, quantity, Integer::sum);
        return Map.of("sku", sku, "added", quantity, "available", updated);
    }

    private int currentStock(String sku) {
        Integer available = stock.get(sku);
        if (available == null) {
            throw new ResourceNotFoundException("Unknown product: " + sku);
        }
        return available;
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new InvalidParameterException("Quantity must be greater than zero");
        }
    }
}
