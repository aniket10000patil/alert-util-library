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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Validates alert JSON against a per-alert-type JSON schema.
 *
 * Schema files are loaded lazily from the classpath on first use for each alertTypeId,
 * then cached for subsequent calls.
 *
 * Schema resolution: {schemaBasePath}/{alertTypeId}_schema.json
 * Example: schemaBasePath=schema, alertTypeId=10000 → classpath:schema/10000_schema.json
 */
public class JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);

    private final String schemaBasePath;
    private final ConcurrentHashMap<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    /**
     * @param schemaBasePath classpath directory containing per-alert-type schema files
     */
    public JsonSchemaValidator(String schemaBasePath) {
        this.schemaBasePath = schemaBasePath.endsWith("/")
                ? schemaBasePath.substring(0, schemaBasePath.length() - 1)
                : schemaBasePath;
    }

    /**
     * Validates the given JSON against the schema for the specified alertTypeId.
     * The schema is loaded and cached on first use for each alertTypeId.
     *
     * @param alertId     used for logging and error messages
     * @param alertTypeId identifies which schema to use (e.g. "10000")
     * @param json        the parsed JSON from the DB view
     * @throws AlertValidationException if JSON fails schema validation
     * @throws IllegalStateException    if the schema file cannot be found or loaded
     */
    public void validate(String alertId, String alertTypeId, JsonNode json) {
        log.debug("Validating alertId [{}] against schema for alertTypeId [{}]", alertId, alertTypeId);

        JsonSchema schema = schemaCache.computeIfAbsent(alertTypeId, this::loadSchema);

        Set<ValidationMessage> errors = schema.validate(json);

        if (!errors.isEmpty()) {
            Set<String> messages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toSet());

            log.warn("Validation failed for alertId [{}] (alertTypeId [{}]): {}", alertId, alertTypeId, messages);
            throw new AlertValidationException(alertId, messages);
        }

        log.debug("Validation passed for alertId [{}] (alertTypeId [{}])", alertId, alertTypeId);
    }

    private JsonSchema loadSchema(String alertTypeId) {
        String schemaPath = schemaBasePath + "/" + alertTypeId + "_schema.json";
        log.info("alert-util: Loading schema for alertTypeId [{}] from [{}]", alertTypeId, schemaPath);

        ClassPathResource resource = new ClassPathResource(schemaPath);

        if (!resource.exists()) {
            throw new IllegalStateException(
                "[alert-util] Schema file not found on classpath: [" + schemaPath + "]\n"
                + "Add the file to your consuming app at: src/main/resources/" + schemaPath
            );
        }

        try (InputStream is = resource.getInputStream()) {
            JsonSchema schema = JsonSchemaFactory
                    .getInstance(SpecVersion.VersionFlag.V7)
                    .getSchema(is);
            log.info("alert-util: Schema loaded and compiled for alertTypeId [{}] from [{}]", alertTypeId, schemaPath);
            return schema;
        } catch (IOException e) {
            throw new IllegalStateException(
                "[alert-util] Failed to load schema for alertTypeId [" + alertTypeId + "] from path [" + schemaPath + "]", e);
        }
    }
}
