package com.example.alertutil.exception;

import java.util.Set;

/**
 * Thrown when the converted JSON fails schema validation.
 */
public class AlertValidationException extends RuntimeException {

    private final Set<String> validationErrors;

    public AlertValidationException(String alertId, Set<String> validationErrors) {
        super("JSON schema validation failed for alertId: " + alertId
                + " | Errors: " + validationErrors);
        this.validationErrors = validationErrors;
    }

    public Set<String> getValidationErrors() {
        return validationErrors;
    }
}
