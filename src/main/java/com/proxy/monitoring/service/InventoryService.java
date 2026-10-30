package com.proxy.monitoring.service;

import com.proxy.monitoring.exception.InvalidParameterException;
import com.proxy.monitoring.exception.ResourceNotFoundException;
import com.proxy.monitoring.exception.UnknownOperationException;
import com.proxy.monitoring.proxy.MicroserviceProxy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;

import static com.proxy.monitoring.service.ServiceSupport.body;
import static com.proxy.monitoring.service.ServiceSupport.integer;
import static com.proxy.monitoring.service.ServiceSupport.simulateLatency;
import static com.proxy.monitoring.service.ServiceSupport.text;

/**
 * Real inventory microservice backed by {@code proxy_workshop.products}.
 * Operations: listProducts, checkStock, reserve, restock.
 */
public class InventoryService implements MicroserviceProxy<Map<String, Object>> {

    private final JdbcClient jdbc;

    public InventoryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Object> execute(String operation, Object... params) {
        simulateLatency(20, 120);
        Map<String, Object> body = body(params);
        return switch (operation) {
            case "listProducts" -> listProducts();
            case "checkStock" -> checkStock(text(body, "sku"));
            case "reserve" -> reserve(text(body, "sku"), integer(body, "quantity"));
            case "restock" -> restock(text(body, "sku"), integer(body, "quantity"));
            default -> throw new UnknownOperationException("inventory", operation);
        };
    }

    private Map<String, Object> listProducts() {
        Map<String, Object> products = new LinkedHashMap<>();
        jdbc.sql("SELECT sku, stock FROM proxy_workshop.products ORDER BY sku")
                .query((rs, row) -> products.put(rs.getString("sku"), rs.getInt("stock")))
                .list();
        return Map.of("products", products);
    }

    private Map<String, Object> checkStock(String sku) {
        return Map.of("sku", sku, "available", currentStock(sku));
    }

    private Map<String, Object> reserve(String sku, int quantity) {
        requirePositive(quantity);
        // Atomic: the stock check and the decrement happen in a single statement.
        Integer remaining = jdbc.sql("""
                UPDATE proxy_workshop.products
                SET stock = stock - :quantity, updated_at = now()
                WHERE sku = :sku AND stock >= :quantity
                RETURNING stock
                """)
                .param("sku", sku)
                .param("quantity", quantity)
                .query(Integer.class)
                .optional()
                .orElse(null);

        if (remaining == null) {
            int available = currentStock(sku);
            throw new InvalidParameterException("Insufficient stock for " + sku
                    + ": requested " + quantity + ", available " + available);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sku", sku);
        result.put("reserved", quantity);
        result.put("remaining", remaining);
        return result;
    }

    private Map<String, Object> restock(String sku, int quantity) {
        requirePositive(quantity);
        Integer updated = jdbc.sql("""
                UPDATE proxy_workshop.products
                SET stock = stock + :quantity, updated_at = now()
                WHERE sku = :sku
                RETURNING stock
                """)
                .param("sku", sku)
                .param("quantity", quantity)
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Unknown product: " + sku));
        return Map.of("sku", sku, "added", quantity, "available", updated);
    }

    private int currentStock(String sku) {
        return jdbc.sql("SELECT stock FROM proxy_workshop.products WHERE sku = :sku")
                .param("sku", sku)
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Unknown product: " + sku));
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new InvalidParameterException("Quantity must be greater than zero");
        }
    }
}
