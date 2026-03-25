package com.example.alertutil.exception;

import java.util.Set;

/**
 * Thrown when JSON returned from the DB view fails schema validation.
 * Contains the full set of validation error messages.
 */
public class AlertValidationException extends RuntimeException {

    private final Long alertInternalId;
    private final Set<String> validationErrors;

    public AlertValidationException(Long alertInternalId, Set<String> validationErrors) {
        super("Schema validation failed for alertInternalId: [" + alertInternalId + "] | Errors: " + validationErrors);
        this.alertInternalId = alertInternalId;
        this.validationErrors = validationErrors;
    }

    public Long getAlertInternalId() { return alertInternalId; }

    public Set<String> getValidationErrors() { return validationErrors; }
}
