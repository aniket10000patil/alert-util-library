package com.example.alertutil.service;

import com.example.alertutil.exception.AlertProcessingException;
import com.example.alertutil.model.AlertResult;
import com.example.alertutil.repository.AlertRepository;
import com.example.alertutil.validator.JsonSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Main service exposed to the consuming Spring Boot application.
 *
 * Pipeline for a given alertId:
 *  1. Query the app-specific DB view → receive a JSON string
 *  2. Parse the JSON string into a JsonNode
 *  3. Validate the JsonNode against the classpath JSON schema
 *  4. Return AlertResult to the caller
 *
 * The DB view is responsible for the HTML → JSON conversion.
 * The consuming app only needs to configure the view name in application.yml.
 *
 * Usage in the consuming app:
 * <pre>
 * {@code
 * @Autowired
 * private AlertService alertService;
 *
 * public void handle(String alertId) {
 *     AlertResult result = alertService.processAlert(alertId);
 *     JsonNode json = result.getJson();
 * }
 * }
 * </pre>
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final JsonSchemaValidator jsonSchemaValidator;
    private final ObjectMapper objectMapper;

    public AlertService(AlertRepository alertRepository,
                        JsonSchemaValidator jsonSchemaValidator,
                        ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.jsonSchemaValidator = jsonSchemaValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes an alert end-to-end:
     *  - Queries the configured DB view for the given alertId
     *  - Parses the returned JSON string
     *  - Validates it against the classpath schema
     *  - Returns the validated AlertResult
     *
     * @param alertId the unique alert identifier
     * @return AlertResult containing the validated JsonNode
     * @throws com.example.alertutil.exception.AlertNotFoundException   if alertId not found in the view
     * @throws com.example.alertutil.exception.AlertValidationException if JSON fails schema validation
     * @throws com.example.alertutil.exception.AlertProcessingException if the view returns malformed JSON
     */
    public AlertResult processAlert(String alertId) {
        log.info("Processing alert [{}]", alertId);

        // Step 1: Query the DB view — returns a JSON string
        log.debug("Step 1 - Querying view for alertId [{}]", alertId);
        String jsonString = alertRepository.fetchJsonByAlertId(alertId);
        log.debug("Step 1 - View returned JSON ({} chars)", jsonString.length());

        // Step 2: Parse JSON string → JsonNode
        log.debug("Step 2 - Parsing JSON string for alertId [{}]", alertId);
        JsonNode json = parseJson(alertId, jsonString);

        // Step 3: Validate against schema
        log.debug("Step 3 - Validating JSON against schema for alertId [{}]", alertId);
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
