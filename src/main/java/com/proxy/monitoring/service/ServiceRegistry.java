package com.proxy.monitoring.service;

import com.proxy.monitoring.exception.ResourceNotFoundException;
import com.proxy.monitoring.proxy.LoggingProxy;
import com.proxy.monitoring.proxy.MicroserviceProxy;
import com.proxy.monitoring.store.LogStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds every real service already wrapped by its {@link LoggingProxy}. Callers never see the raw services.
 */
@Component
public class ServiceRegistry {

    public static final String INVENTORY = "inventory";
    public static final String ORDERS = "orders";
    public static final String PAYMENTS = "payments";

    private final Map<String, MicroserviceProxy<Map<String, Object>>> services = new LinkedHashMap<>();

    public ServiceRegistry(LogStore store, @Value("${app.payment.failure-rate:0.10}") double paymentFailureRate) {
        services.put(INVENTORY, new LoggingProxy<>(INVENTORY, new InventoryService(), store));
        services.put(ORDERS, new LoggingProxy<>(ORDERS, new OrderService(), store));
        services.put(PAYMENTS, new LoggingProxy<>(PAYMENTS, new PaymentService(paymentFailureRate), store));
    }

    public MicroserviceProxy<Map<String, Object>> get(String serviceId) {
        MicroserviceProxy<Map<String, Object>> service = services.get(serviceId);
        if (service == null) {
            throw new ResourceNotFoundException("Unknown service: " + serviceId);
        }
        return service;
    }

    public List<String> ids() {
        return List.copyOf(services.keySet());
    }
}
