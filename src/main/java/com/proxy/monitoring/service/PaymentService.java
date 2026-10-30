package com.proxy.monitoring.service;

import com.proxy.monitoring.exception.InvalidParameterException;
import com.proxy.monitoring.exception.ResourceNotFoundException;
import com.proxy.monitoring.exception.ServiceFailureException;
import com.proxy.monitoring.exception.UnknownOperationException;
import com.proxy.monitoring.proxy.MicroserviceProxy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static com.proxy.monitoring.service.ServiceSupport.body;
import static com.proxy.monitoring.service.ServiceSupport.decimal;
import static com.proxy.monitoring.service.ServiceSupport.simulateLatency;
import static com.proxy.monitoring.service.ServiceSupport.text;

/**
 * Real payment microservice. Operations: charge, refund, status.
 * A configurable share of calls (10% by default) fails on purpose to simulate gateway outages.
 */
public class PaymentService implements MicroserviceProxy<Map<String, Object>> {

    private final Map<String, Map<String, Object>> payments = new ConcurrentHashMap<>();
    private final double failureRate;

    public PaymentService(double failureRate) {
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
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("paymentId", paymentId);
        payment.put("orderId", orderId);
        payment.put("amount", amount);
        payment.put("status", "APPROVED");
        payment.put("processedAt", Instant.now().toString());
        payments.put(paymentId, payment);
        return payment;
    }

    private Map<String, Object> refund(String paymentId) {
        Map<String, Object> payment = find(paymentId);
        payment.put("status", "REFUNDED");
        return payment;
    }

    private Map<String, Object> find(String paymentId) {
        Map<String, Object> payment = payments.get(paymentId);
        if (payment == null) {
            throw new ResourceNotFoundException("Payment not found: " + paymentId);
        }
        return payment;
    }
}
