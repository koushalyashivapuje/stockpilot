package com.stockpilot.exception;

/** Thrown for state conflicts: duplicate SKU, invalid order transition, double cancellation, etc. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
