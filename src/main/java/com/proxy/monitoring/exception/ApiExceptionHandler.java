package com.proxy.monitoring.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({InvalidParameterException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, Object>> badRequest(RuntimeException ex) {
        return error(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler({UnknownOperationException.class, ResourceNotFoundException.class})
    ResponseEntity<Map<String, Object>> notFound(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ServiceFailureException.class)
    ResponseEntity<Map<String, Object>> serviceFailure(ServiceFailureException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, RuntimeException ex) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", String.valueOf(ex.getMessage())));
    }
}
