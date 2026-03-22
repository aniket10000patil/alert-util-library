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
 * Validates alert JSON against a JSON schema.
 *
 * The schema path is provided per call and resolved from the consuming app's classpath.
 * Schemas are loaded and compiled on first use then cached by path for subsequent calls.
 */
public class JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);

    private final ConcurrentHashMap<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    /**
     * Validates the given JSON against the schema at the specified classpath path.
     * The schema is loaded and cached on first use for each schemaPath.
     *
     * @param alertId    used for logging and error messages
     * @param schemaPath classpath path to the JSON schema file (e.g. schema/10000_schema.json)
     * @param json       the parsed JSON from the DB view
     * @throws AlertValidationException if JSON fails schema validation
     * @throws IllegalStateException    if the schema file cannot be found or loaded
     */
    public void validate(String alertId, String schemaPath, JsonNode json) {
        log.debug("Validating alertId [{}] against schema [{}]", alertId, schemaPath);

        JsonSchema schema = schemaCache.computeIfAbsent(schemaPath, this::loadSchema);

        Set<ValidationMessage> errors = schema.validate(json);

        if (!errors.isEmpty()) {
            Set<String> messages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toSet());

            log.warn("Validation failed for alertId [{}] (schema [{}]): {}", alertId, schemaPath, messages);
            throw new AlertValidationException(alertId, messages);
        }

        log.debug("Validation passed for alertId [{}] (schema [{}])", alertId, schemaPath);
    }

    private JsonSchema loadSchema(String schemaPath) {
        log.info("alert-util: Loading schema from [{}]", schemaPath);

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
            log.info("alert-util: Schema loaded and compiled from [{}]", schemaPath);
            return schema;
        } catch (IOException e) {
            throw new IllegalStateException(
                "[alert-util] Failed to load schema from path [" + schemaPath + "]", e);
        }
    }
}
