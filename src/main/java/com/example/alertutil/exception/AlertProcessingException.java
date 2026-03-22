package com.example.alertutil.exception;

/**
 * Thrown when the DB view returns data that cannot be processed.
 * Examples: malformed JSON, CLOB read failure.
 */
public class AlertProcessingException extends RuntimeException {

    private final Long alertInternalId;

    public AlertProcessingException(Long alertInternalId, String reason, Throwable cause) {
        super("Failed to process alert [" + alertInternalId + "]: " + reason, cause);
        this.alertInternalId = alertInternalId;
    }

    public Long getAlertInternalId() { return alertInternalId; }
}
