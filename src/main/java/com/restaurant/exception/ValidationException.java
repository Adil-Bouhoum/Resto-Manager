package com.restaurant.exception;

public class ValidationException extends RestaurantException {
    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
