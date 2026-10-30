package com.proxy.monitoring.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

/**
 * Fires random but coherent calls through the logging proxies so the dashboard has data to show.
 * Orders and payments reference entities created earlier in the same run, so the only errors
 * are the intentional payment failures and the occasional out-of-stock reservation.
 */
@Component
public class LoadSimulator {

    private static final List<String> SKUS =
            List.of("SKU-LAPTOP", "SKU-MOUSE", "SKU-KEYBOARD", "SKU-MONITOR", "SKU-HEADSET");

    private final ServiceRegistry registry;

    public LoadSimulator(ServiceRegistry registry) {
        this.registry = registry;
    }

    public Map<String, Object> run(int calls) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<String> orderIds = new ArrayList<>();
        List<String> paymentIds = new ArrayList<>();
        int succeeded = 0;
        int failed = 0;

        for (int i = 0; i < calls; i++) {
            try {
                step(random, orderIds, paymentIds);
                succeeded++;
            } catch (RuntimeException ex) {
                failed++;
            }
        }
        return Map.of("requested", calls, "succeeded", succeeded, "failed", failed);
    }

    private void step(ThreadLocalRandom random, List<String> orderIds, List<String> paymentIds) {
        String sku = SKUS.get(random.nextInt(SKUS.size()));
        int roll = random.nextInt(10);

        switch (roll) {
            case 0 -> call(ServiceRegistry.INVENTORY, "checkStock", Map.of("sku", sku));
            case 1 -> call(ServiceRegistry.INVENTORY, "reserve", Map.of("sku", sku, "quantity", random.nextInt(1, 4)));
            case 2 -> call(ServiceRegistry.INVENTORY, random.nextBoolean() ? "listProducts" : "restock",
                    Map.of("sku", sku, "quantity", random.nextInt(5, 20)));
            case 3, 4 -> orderIds.add(String.valueOf(call(ServiceRegistry.ORDERS, "create", Map.of(
                    "customerId", "CUS-" + random.nextInt(1, 50), "sku", sku,
                    "quantity", random.nextInt(1, 6), "unitPrice", random.nextInt(20, 900))).get("orderId")));
            case 5 -> {
                if (orderIds.isEmpty()) {
                    call(ServiceRegistry.ORDERS, "list", Map.of());
                } else {
                    call(ServiceRegistry.ORDERS, "get", Map.of("orderId", pick(random, orderIds)));
                }
            }
            case 6, 7, 8 -> {
                if (orderIds.isEmpty()) {
                    call(ServiceRegistry.ORDERS, "list", Map.of());
                } else {
                    paymentIds.add(String.valueOf(call(ServiceRegistry.PAYMENTS, "charge", Map.of(
                            "orderId", pick(random, orderIds), "amount", random.nextInt(20, 2000))).get("paymentId")));
                }
            }
            default -> {
                if (paymentIds.isEmpty()) {
                    call(ServiceRegistry.INVENTORY, "checkStock", Map.of("sku", sku));
                } else {
                    call(ServiceRegistry.PAYMENTS, "status", Map.of("paymentId", pick(random, paymentIds)));
                }
            }
        }
    }

    private String pick(ThreadLocalRandom random, List<String> ids) {
        return ids.get(random.nextInt(ids.size()));
    }

    private Map<String, Object> call(String serviceId, String operation, Map<String, Object> body) {
        return registry.get(serviceId).execute(operation, body);
    }
}
