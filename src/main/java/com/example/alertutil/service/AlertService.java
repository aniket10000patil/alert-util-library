package com.example.alertutil.service;

import com.example.alertutil.exception.AlertProcessingException;
import com.example.alertutil.model.AlertResult;
import com.example.alertutil.repository.AlertRepository;
import com.example.alertutil.validator.JsonSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the consuming application.
 *
 * Orchestrates the full alert processing pipeline:
 *   1. Query the configured DB view by alertId → returns JSON string
 *   2. Parse the JSON string into a JsonNode
 *   3. Extract alert type from JSON, validate against matching schema
 *   4. Return AlertResult to the caller
 *
 * Usage in the consuming app:
 *
 *   @Autowired
 *   private AlertService alertService;
 *
 *   AlertResult result = alertService.processAlert("ALERT-001");
 *   JsonNode json = result.getJson();
 */
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository     alertRepository;
    private final JsonSchemaValidator jsonSchemaValidator;
    private final ObjectMapper        objectMapper;

    public AlertService(AlertRepository alertRepository,
                        JsonSchemaValidator jsonSchemaValidator,
                        ObjectMapper objectMapper) {
        this.alertRepository     = alertRepository;
        this.jsonSchemaValidator  = jsonSchemaValidator;
        this.objectMapper         = objectMapper;
    }

    /**
     * Processes an alert end-to-end.
     *
     * @param alertId the unique alert identifier
     * @return AlertResult containing the validated JsonNode
     *
     * @throws com.example.alertutil.exception.AlertNotFoundException   if no row found in DB view
     * @throws com.example.alertutil.exception.AlertValidationException if JSON fails schema validation
     * @throws com.example.alertutil.exception.AlertProcessingException if DB view returns malformed JSON
     */
    public AlertResult processAlert(String alertId) {
        log.info("Processing alert [{}]", alertId);

        // Step 1 — query the DB view
        log.debug("Step 1 - Querying view for alertId [{}]", alertId);
        String jsonString = alertRepository.fetchJsonByAlertId(alertId);

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
