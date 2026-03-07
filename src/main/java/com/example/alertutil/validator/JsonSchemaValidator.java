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
import java.util.stream.Collectors;

/**
 * Validates a JsonNode against a JSON Schema loaded from the classpath.
 *
 * The schema path is configurable via:
 *   alert-util.schema-path=schema/alert-schema.json  (default)
 */
public class JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);

    private final JsonSchema schema;

    public JsonSchemaValidator(String schemaClasspathPath) {
        this.schema = loadSchema(schemaClasspathPath);
    }

    /**
     * Validates the given JsonNode.
     *
     * @param alertId used for error reporting
     * @param json    the JSON to validate
     * @throws AlertValidationException if validation fails
     */
    public void validate(String alertId, JsonNode json) {
        Set<ValidationMessage> errors = schema.validate(json);

        if (!errors.isEmpty()) {
            Set<String> messages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toSet());

            log.warn("Schema validation failed for alertId [{}]: {}", alertId, messages);
            throw new AlertValidationException(alertId, messages);
        }

        log.debug("Schema validation passed for alertId [{}]", alertId);
    }

    private JsonSchema loadSchema(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                throw new IllegalStateException(
                        "JSON schema not found on classpath at: " + path
                                + "\nPlease add the schema file to src/main/resources/" + path);
            }
            try (InputStream is = resource.getInputStream()) {
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
                JsonSchema loaded = factory.getSchema(is);
                log.info("Loaded JSON schema from classpath: {}", path);
                return loaded;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load JSON schema from: " + path, e);
        }
    }
}
