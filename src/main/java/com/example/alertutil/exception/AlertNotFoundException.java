package com.example.alertutil.exception;

/**
 * Thrown when no alert is found for the given alertId in the database.
 */
public class AlertNotFoundException extends RuntimeException {
    public AlertNotFoundException(String alertId) {
        super("No alert found for alertId: " + alertId);
    }
}
