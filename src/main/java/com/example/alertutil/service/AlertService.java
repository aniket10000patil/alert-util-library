package com.example.alertutil.service;

import com.example.alertutil.exception.AlertProcessingException;
import com.example.alertutil.model.AlertResult;
import com.example.alertutil.repository.AlertRepository;
import com.example.alertutil.validator.JsonSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Main entry point for the consuming application.
 *
 * Orchestrates the full alert processing pipeline:
 *   1. Resolve the DB view name — either from the static default or from the DB config map
 *   2. Query the resolved view by alertId → returns JSON string
 *   3. Parse the JSON string into a JsonNode
 *   4. Extract alert type from JSON, validate against matching schema
 *   5. Return AlertResult to the caller
 *
 * Usage in the consuming app (single view):
 *
 *   AlertResult result = alertService.processAlert("ALERT-001");
 *
 * Usage when multiple views are driven by a DB config table:
 *
 *   AlertResult result = alertService.processAlert("ALERT-001", "10000");
 *   // "10000" is looked up in the DB config map → returns the mapped view name
 */
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository     alertRepository;
    private final JsonSchemaValidator jsonSchemaValidator;
    private final ObjectMapper        objectMapper;

    /** View name to use when no viewKey is supplied (from alert-util.view-name). May be null. */
    private final String defaultViewName;

    /**
     * Map of viewKey → viewName loaded from the DB config table at startup.
     * Empty when no view-config-table is configured.
     */
    private final Map<String, String> viewConfigMap;

    public AlertService(AlertRepository alertRepository,
                        JsonSchemaValidator jsonSchemaValidator,
                        ObjectMapper objectMapper,
                        String defaultViewName,
                        Map<String, String> viewConfigMap) {
        this.alertRepository     = alertRepository;
        this.jsonSchemaValidator  = jsonSchemaValidator;
        this.objectMapper         = objectMapper;
        this.defaultViewName      = defaultViewName;
        this.viewConfigMap        = viewConfigMap != null ? viewConfigMap : Collections.emptyMap();
    }

    /**
     * Processes an alert using the default (statically configured) view name.
     *
     * @param alertId the unique alert identifier
     * @return AlertResult containing the validated JsonNode
     *
     * @throws com.example.alertutil.exception.AlertNotFoundException   if no row found in DB view
     * @throws com.example.alertutil.exception.AlertValidationException if JSON fails schema validation
     * @throws com.example.alertutil.exception.AlertProcessingException if DB view returns malformed JSON
     * @throws IllegalStateException if alert-util.view-name is not configured
     */
    public AlertResult processAlert(String alertId) {
        if (defaultViewName == null || defaultViewName.isBlank()) {
            throw new IllegalStateException(
                "[alert-util] No default view configured. "
                + "Set alert-util.view-name, or call processAlert(alertId, viewKey) "
                + "to select a view from the DB config table.");
        }
        return processAlertWithView(alertId, defaultViewName);
    }

    /**
     * Processes an alert by resolving the view name from the DB config table using viewKey.
     *
     * The DB config table must be configured via alert-util.view-config-table, and the
     * table must contain a row with the given viewKey (e.g. alert type "10000").
     *
     * @param alertId the unique alert identifier
     * @param viewKey the key used to look up the view name in the DB config map
     * @return AlertResult containing the validated JsonNode
     *
     * @throws IllegalArgumentException if viewKey is not found in the DB config map
     * @throws com.example.alertutil.exception.AlertNotFoundException   if no row found in DB view
     * @throws com.example.alertutil.exception.AlertValidationException if JSON fails schema validation
     * @throws com.example.alertutil.exception.AlertProcessingException if DB view returns malformed JSON
     */
    public AlertResult processAlert(String alertId, String viewKey) {
        if (viewConfigMap.isEmpty()) {
            throw new IllegalStateException(
                "[alert-util] No DB view config map available. "
                + "Set alert-util.view-config-table to enable DB-driven view selection.");
        }
        String viewName = viewConfigMap.get(viewKey);
        if (viewName == null) {
            throw new IllegalArgumentException(
                "[alert-util] No view configured for key [" + viewKey + "]. "
                + "Known keys: " + viewConfigMap.keySet());
        }
        return processAlertWithView(alertId, viewName);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private AlertResult processAlertWithView(String alertId, String viewName) {
        log.info("Processing alert [{}] using view [{}]", alertId, viewName);

        // Step 1 — query the DB view
        log.debug("Step 1 - Querying view [{}] for alertId [{}]", viewName, alertId);
        String jsonString = alertRepository.fetchJsonByAlertId(alertId, viewName);

        // Step 2 — parse JSON string into JsonNode
        log.debug("Step 2 - Parsing JSON for alertId [{}]", alertId);
        JsonNode json = parseJson(alertId, jsonString);

        // Step 3 — validate against the schema for this alert type
        log.debug("Step 3 - Validating alertId [{}]", alertId);
        jsonSchemaValidator.validate(alertId, json);

        log.info("Alert [{}] processed successfully", alertId);
        return new AlertResult(alertId, json);
    }

    private JsonNode parseJson(String alertId, String jsonString) {
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            throw new AlertProcessingException(
                    alertId, "DB view returned invalid JSON: " + e.getMessage(), e);
        }
    }
}
