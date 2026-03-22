package com.example.alertutil.exception;

/**
 * Thrown when no alert is found in the DB view for the given alertInternalId.
 */
public class AlertNotFoundException extends RuntimeException {

    private final Long alertInternalId;

    public AlertNotFoundException(Long alertInternalId) {
        super("No alert found for alertInternalId: [" + alertInternalId + "]");
        this.alertInternalId = alertInternalId;
    }

    public Long getAlertInternalId() { return alertInternalId; }
}
