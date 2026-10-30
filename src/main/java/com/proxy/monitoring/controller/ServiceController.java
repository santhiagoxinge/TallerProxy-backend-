package com.proxy.monitoring.controller;

import com.proxy.monitoring.service.ServiceRegistry;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceRegistry registry;

    public ServiceController(ServiceRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/inventory/{operation}")
    public Map<String, Object> inventory(@PathVariable String operation,
                                         @RequestBody(required = false) Map<String, Object> body) {
        return call(ServiceRegistry.INVENTORY, operation, body);
    }

    @PostMapping("/orders/{operation}")
    public Map<String, Object> orders(@PathVariable String operation,
                                      @RequestBody(required = false) Map<String, Object> body) {
        return call(ServiceRegistry.ORDERS, operation, body);
    }

    @PostMapping("/payments/{operation}")
    public Map<String, Object> payments(@PathVariable String operation,
                                        @RequestBody(required = false) Map<String, Object> body) {
        return call(ServiceRegistry.PAYMENTS, operation, body);
    }

    private Map<String, Object> call(String serviceId, String operation, Map<String, Object> body) {
        return registry.get(serviceId).execute(operation, body == null ? Map.of() : body);
    }
}
