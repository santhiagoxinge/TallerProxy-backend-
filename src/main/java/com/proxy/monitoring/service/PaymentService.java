package com.proxy.monitoring.service;

import com.proxy.monitoring.exception.InvalidParameterException;
import com.proxy.monitoring.exception.ResourceNotFoundException;
import com.proxy.monitoring.exception.ServiceFailureException;
import com.proxy.monitoring.exception.UnknownOperationException;
import com.proxy.monitoring.proxy.MicroserviceProxy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.jdbc.core.simple.JdbcClient;

import static com.proxy.monitoring.service.ServiceSupport.body;
import static com.proxy.monitoring.service.ServiceSupport.decimal;
import static com.proxy.monitoring.service.ServiceSupport.simulateLatency;
import static com.proxy.monitoring.service.ServiceSupport.text;

/**
 * Real payment microservice backed by {@code proxy_workshop.payments}. Operations: charge, refund, status.
 * A configurable share of calls (10% by default) fails on purpose to simulate gateway outages.
 */
public class PaymentService implements MicroserviceProxy<Map<String, Object>> {

    private static final String COLUMNS = "payment_id, order_id, amount, status, processed_at";

    private final JdbcClient jdbc;
    private final double failureRate;

    public PaymentService(JdbcClient jdbc, double failureRate) {
        this.jdbc = jdbc;
        this.failureRate = failureRate;
    }

    @Override
    public Map<String, Object> execute(String operation, Object... params) {
        simulateLatency(60, 260);
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new ServiceFailureException("Payment gateway unavailable (simulated failure)");
        }
        Map<String, Object> body = body(params);
        return switch (operation) {
            case "charge" -> charge(text(body, "orderId"), decimal(body, "amount"));
            case "refund" -> refund(text(body, "paymentId"));
            case "status" -> find(text(body, "paymentId"));
            default -> throw new UnknownOperationException("payments", operation);
        };
    }

    private Map<String, Object> charge(String orderId, double amount) {
        if (amount <= 0) {
            throw new InvalidParameterException("Amount must be greater than zero");
        }
        String orderStatus = jdbc.sql("SELECT status FROM proxy_workshop.orders WHERE order_id = :orderId")
                .param("orderId", orderId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!"CREATED".equals(orderStatus)) {
            throw new InvalidParameterException("Order " + orderId + " is " + orderStatus + " and cannot be charged");
        }

        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return jdbc.sql("""
                INSERT INTO proxy_workshop.payments (payment_id, order_id, amount, status)
                VALUES (:paymentId, :orderId, :amount, 'APPROVED')
                RETURNING\s""" + COLUMNS)
                .param("paymentId", paymentId)
                .param("orderId", orderId)
                .param("amount", BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP))
                .query(this::mapPayment)
                .single();
    }

    private Map<String, Object> refund(String paymentId) {
        return jdbc.sql("UPDATE proxy_workshop.payments SET status = 'REFUNDED' WHERE payment_id = :paymentId RETURNING "
                        + COLUMNS)
                .param("paymentId", paymentId)
                .query(this::mapPayment)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }

    private Map<String, Object> find(String paymentId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM proxy_workshop.payments WHERE payment_id = :paymentId")
                .param("paymentId", paymentId)
                .query(this::mapPayment)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
    }

    private Map<String, Object> mapPayment(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("paymentId", rs.getString("payment_id"));
        payment.put("orderId", rs.getString("order_id"));
        payment.put("amount", rs.getBigDecimal("amount").doubleValue());
        payment.put("status", rs.getString("status"));
        payment.put("processedAt", rs.getObject("processed_at", OffsetDateTime.class).toInstant().toString());
        return payment;
    }
}
