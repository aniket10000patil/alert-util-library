package com.example.alertutil.validator;

import com.example.alertutil.exception.AlertValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates alert JSON against the correct schema based on the alert type.
 *
 * All schemas are loaded and compiled at startup from the consuming app's classpath.
 * The alertType is extracted from the JSON itself, then the matching schema is used.
 *
 * Flow:
 *   1. Read json.get(alertTypeField) → e.g. "10000"
 *   2. Look up compiled schema for "10000"
 *   3. Run validation → throw AlertValidationException if errors found
 */
public class JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);

    /**
     * Key   = alertType string (e.g. "10000")
     * Value = compiled JsonSchema ready for validation
     *
     * Loaded once at startup, reused for every validation call.
     */
    private final Map<String, JsonSchema> compiledSchemas;

    /**
     * The JSON field name that holds the alert type value.
     * Configurable via alert-util.alert-type-field (default: "alertType")
     */
    private final String alertTypeField;

    /**
     * @param schemaPathMap   map of alertType → classpath path to schema file
     * @param alertTypeField  field name in JSON that holds the alert type
     */
    public JsonSchemaValidator(Map<String, String> schemaPathMap, String alertTypeField) {
        this.alertTypeField   = alertTypeField;
        this.compiledSchemas  = loadAllSchemas(schemaPathMap);
    }

    /**
     * Validates the given JSON using the schema for its alert type.
     *
     * @param alertId used for logging and error messages
     * @param json    the parsed JSON from the DB view
     * @throws AlertValidationException if the alert type is unknown or JSON fails validation
     */
    public void validate(String alertId, JsonNode json) {

        // Step 1 — extract alert type from JSON
        String alertType = extractAlertType(alertId, json);

        // Step 2 — look up compiled schema for this alert type
        JsonSchema schema = compiledSchemas.get(alertType);
        if (schema == null) {
            throw new AlertValidationException(
                alertId,
                Set.of("No schema configured for alertType [" + alertType + "]. "
                     + "Add it under alert-util.schema-map in application.yml")
            );
        }

        log.debug("Validating alertId [{}] against schema for type [{}]", alertId, alertType);

        // Step 3 — validate JSON against schema
        Set<ValidationMessage> errors = schema.validate(json);

        if (!errors.isEmpty()) {
            Set<String> messages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toSet());

            log.warn("Validation failed for alertId [{}] type [{}]: {}", alertId, alertType, messages);
            throw new AlertValidationException(alertId, messages);
        }

        log.debug("Validation passed for alertId [{}] type [{}]", alertId, alertType);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Reads the alertType field value from the JSON.
     * Throws clearly if the field is absent so the developer knows exactly what to fix.
     */
    private String extractAlertType(String alertId, JsonNode json) {
        JsonNode typeNode = json.get(alertTypeField);

        if (typeNode == null || typeNode.isNull()) {
            throw new AlertValidationException(
                alertId,
                Set.of("JSON is missing field [" + alertTypeField + "]. "
                     + "The DB view must return this field so the library can "
                     + "select the correct schema. "
                     + "Override the field name via alert-util.alert-type-field if needed.")
            );
        }

        return typeNode.asText();
    }

    /**
     * Loads and compiles all schemas at startup.
     * Fails fast if any schema file is missing or unparseable —
     * better to fail at startup than on the first real request.
     */
    private Map<String, JsonSchema> loadAllSchemas(Map<String, String> schemaPathMap) {
        if (schemaPathMap == null || schemaPathMap.isEmpty()) {
            throw new IllegalStateException(
                "[alert-util] 'alert-util.schema-map' is missing or empty.\n"
                + "Add it to your application.yml:\n\n"
                + "  alert-util:\n"
                + "    schema-map:\n"
                + "      10000: schema/schema-10000.json\n"
                + "      20000: schema/schema-20000.json"
            );
        }

        Map<String, JsonSchema> compiled = new HashMap<>();

        for (Map.Entry<String, String> entry : schemaPathMap.entrySet()) {
            String alertType  = entry.getKey();
            String schemaPath = entry.getValue();
            log.info("alert-util: Loading schema for alertType [{}] from [{}]", alertType, schemaPath);
            compiled.put(alertType, loadSingleSchema(alertType, schemaPath));
        }

        log.info("alert-util: Loaded {} schema(s) for types: {}", compiled.size(), compiled.keySet());
        return compiled;
    }

    /**
     * Loads and compiles a single JSON Schema file from the consuming app's classpath.
     */
    private JsonSchema loadSingleSchema(String alertType, String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);

            if (!resource.exists()) {
                throw new IllegalStateException(
                    "[alert-util] Schema file for alertType [" + alertType + "] "
                    + "not found on classpath: [" + path + "]\n"
                    + "Add the file to your consuming app at: src/main/resources/" + path
                );
            }

            try (InputStream is = resource.getInputStream()) {
                return JsonSchemaFactory
                        .getInstance(SpecVersion.VersionFlag.V7)
                        .getSchema(is);
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                "[alert-util] Failed to load schema for alertType [" + alertType
                + "] from path [" + path + "]", e);
        }
    }
}
