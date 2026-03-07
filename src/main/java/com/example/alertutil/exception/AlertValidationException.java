package com.example.alertutil.exception;

import java.util.Set;

/**
 * Thrown when JSON returned from the DB view fails schema validation.
 * Contains the full set of validation error messages.
 */
public class AlertValidationException extends RuntimeException {

    private final String alertId;
    private final Set<String> validationErrors;

    public AlertValidationException(String alertId, Set<String> validationErrors) {
        super("Schema validation failed for alertId: [" + alertId + "] | Errors: " + validationErrors);
        this.alertId = alertId;
        this.validationErrors = validationErrors;
    }

    public String getAlertId() { return alertId; }

    public Set<String> getValidationErrors() { return validationErrors; }
}
