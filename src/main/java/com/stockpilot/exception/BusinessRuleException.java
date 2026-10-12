package com.stockpilot.exception;

/** Thrown for general business rule violations: insufficient stock, invalid adjustment, inactive product, etc. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
