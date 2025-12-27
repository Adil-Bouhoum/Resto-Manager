package com.restaurant.exception;

public class DatabaseException extends RestaurantException {
    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}