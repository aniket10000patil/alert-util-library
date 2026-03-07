package com.example.alertutil.exception;

/**
 * Thrown when the DB view returns data that cannot be processed.
 * Examples: malformed JSON, CLOB read failure.
 */
public class AlertProcessingException extends RuntimeException {

    private final String alertId;

    public AlertProcessingException(String alertId, String reason, Throwable cause) {
        super("Failed to process alert [" + alertId + "]: " + reason, cause);
        this.alertId = alertId;
    }

    public String getAlertId() { return alertId; }
}
