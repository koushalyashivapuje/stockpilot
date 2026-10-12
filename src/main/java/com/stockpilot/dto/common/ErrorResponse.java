package com.stockpilot.dto.common;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, List.of());
    }

    public static ErrorResponse withFieldErrors(int status, String error, String message, String path,
                                                 List<FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }

    public static ErrorResponse withFieldErrors(int status, String error, String message, String path,
                                                 Map<String, String> fieldErrorMap) {
        List<FieldError> list = fieldErrorMap.entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue()))
                .toList();
        return new ErrorResponse(Instant.now(), status, error, message, path, list);
    }
}
