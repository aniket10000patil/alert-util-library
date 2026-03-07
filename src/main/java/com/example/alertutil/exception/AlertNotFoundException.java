package com.example.alertutil.exception;

/**
 * Thrown when no alert is found in the DB view for the given alertId.
 */
public class AlertNotFoundException extends RuntimeException {

    private final String alertId;

    public AlertNotFoundException(String alertId) {
        super("No alert found for alertId: [" + alertId + "]");
        this.alertId = alertId;
    }

    public String getAlertId() { return alertId; }
}
