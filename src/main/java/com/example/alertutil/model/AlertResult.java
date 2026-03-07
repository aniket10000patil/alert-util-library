package com.example.alertutil.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The result returned to the consuming application after:
 *  1. Fetching HTML from DB
 *  2. Converting HTML → JSON
 *  3. Validating JSON against schema
 */
public class AlertResult {

    private final String alertId;
    private final JsonNode json;

    public AlertResult(String alertId, JsonNode json) {
        this.alertId = alertId;
        this.json = json;
    }

    public String getAlertId() { return alertId; }
    public JsonNode getJson()  { return json; }

    @Override
    public String toString() {
        return "AlertResult{alertId='" + alertId + "', json=" + json + "}";
    }
}
