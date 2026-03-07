package com.example.alertutil.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Returned to the consuming application after successful processing.
 * Contains the alertId and the fully validated JSON.
 */
public class AlertResult {

    private final String alertId;
    private final JsonNode json;

    public AlertResult(String alertId, JsonNode json) {
        this.alertId = alertId;
        this.json    = json;
    }

    public String   getAlertId() { return alertId; }
    public JsonNode getJson()    { return json; }

    @Override
    public String toString() {
        return "AlertResult{alertId='" + alertId + "', json=" + json + "}";
    }
}
