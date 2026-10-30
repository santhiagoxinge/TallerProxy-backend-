package com.proxy.monitoring.service;

import com.proxy.monitoring.exception.InvalidParameterException;
import com.proxy.monitoring.exception.ResourceNotFoundException;
import com.proxy.monitoring.exception.UnknownOperationException;
import com.proxy.monitoring.proxy.MicroserviceProxy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;

import static com.proxy.monitoring.service.ServiceSupport.body;
import static com.proxy.monitoring.service.ServiceSupport.decimal;
import static com.proxy.monitoring.service.ServiceSupport.integer;
import static com.proxy.monitoring.service.ServiceSupport.simulateLatency;
import static com.proxy.monitoring.service.ServiceSupport.text;

/**
 * Real order microservice backed by {@code proxy_workshop.orders}.
 * Operations: create, get, cancel, list.
 */
public class OrderService implements MicroserviceProxy<Map<String, Object>> {

    private static final String COLUMNS = "order_id, customer_id, sku, quantity, total, status, created_at";
    private static final int LIST_LIMIT = 50;

    private final JdbcClient jdbc;

    public OrderService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Object> execute(String operation, Object... params) {
        simulateLatency(30, 160);
        Map<String, Object> body = body(params);
        return switch (operation) {
            case "create" -> create(text(body, "customerId"), text(body, "sku"),
                    integer(body, "quantity"), decimal(body, "unitPrice"));
            case "get" -> find(text(body, "orderId"));
            case "cancel" -> cancel(text(body, "orderId"));
            case "list" -> Map.of("orders", list());
            default -> throw new UnknownOperationException("orders", operation);
        };
    }

    private Map<String, Object> create(String customerId, String sku, int quantity, double unitPrice) {
        if (quantity <= 0) {
            throw new InvalidParameterException("Quantity must be greater than zero");
        }
        if (unitPrice <= 0) {
            throw new InvalidParameterException("Unit price must be greater than zero");
        }
        Integer products = jdbc.sql("SELECT count(*) FROM proxy_workshop.products WHERE sku = :sku")
                .param("sku", sku)
                .query(Integer.class)
                .single();
        if (products == 0) {
            throw new ResourceNotFoundException("Unknown product: " + sku);
        }

        BigDecimal total = BigDecimal.valueOf(unitPrice).multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);

        return jdbc.sql("""
                INSERT INTO proxy_workshop.orders (order_id, customer_id, sku, quantity, total, status)
                VALUES ('ORD-' || nextval('proxy_workshop.order_seq'), :customerId, :sku, :quantity, :total, 'CREATED')
                RETURNING\s""" + COLUMNS)
                .param("customerId", customerId)
                .param("sku", sku)
                .param("quantity", quantity)
                .param("total", total)
                .query(this::mapOrder)
                .single();
    }

    private Map<String, Object> find(String orderId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM proxy_workshop.orders WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query(this::mapOrder)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private Map<String, Object> cancel(String orderId) {
        return jdbc.sql("UPDATE proxy_workshop.orders SET status = 'CANCELLED' WHERE order_id = :orderId RETURNING "
                        + COLUMNS)
                .param("orderId", orderId)
                .query(this::mapOrder)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private List<Map<String, Object>> list() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM proxy_workshop.orders ORDER BY created_at DESC LIMIT :limit")
                .param("limit", LIST_LIMIT)
                .query(this::mapOrder)
                .list();
    }

    private Map<String, Object> mapOrder(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", rs.getString("order_id"));
        order.put("customerId", rs.getString("customer_id"));
        order.put("sku", rs.getString("sku"));
        order.put("quantity", rs.getInt("quantity"));
        order.put("total", rs.getBigDecimal("total").doubleValue());
        order.put("status", rs.getString("status"));
        order.put("createdAt", rs.getObject("created_at", OffsetDateTime.class).toInstant().toString());
        return order;
    }
}
