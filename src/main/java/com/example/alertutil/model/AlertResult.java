package com.example.alertutil.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Returned to the consuming application after successful processing.
 * Contains the alertInternalId and the fully validated JSON.
 */
public class AlertResult {

    private final Long alertInternalId;
    private final JsonNode json;

    public AlertResult(Long alertInternalId, JsonNode json) {
        this.alertInternalId = alertInternalId;
        this.json            = json;
    }

    public Long     getAlertInternalId() { return alertInternalId; }
    public JsonNode getJson()            { return json; }

    @Override
    public String toString() {
        return "AlertResult{alertInternalId=" + alertInternalId + ", json=" + json + "}";
    }
}
