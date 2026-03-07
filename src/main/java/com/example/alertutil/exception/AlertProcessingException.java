package com.example.alertutil.exception;

/**
 * Thrown when the DB view returns a value that cannot be parsed as valid JSON.
 * This typically means the view's SQL conversion logic has an issue.
 */
public class AlertProcessingException extends RuntimeException {

    public AlertProcessingException(String alertId, String reason, Throwable cause) {
        super("Failed to process alert [" + alertId + "]: " + reason, cause);
    }
}
