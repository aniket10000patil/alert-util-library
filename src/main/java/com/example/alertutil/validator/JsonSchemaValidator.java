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
 * Validates alert JSON against a single JSON schema.
 *
 * The schema is loaded and compiled once at startup from the consuming app's classpath.
 */
public class JsonSchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);

    private final JsonSchema compiledSchema;

    /**
     * @param schemaPath classpath path to the JSON schema file
     */
    public JsonSchemaValidator(String schemaPath) {
        this.compiledSchema = loadSchema(schemaPath);
    }

    /**
     * Validates the given JSON against the configured schema.
     *
     * @param alertId used for logging and error messages
     * @param json    the parsed JSON from the DB view
     * @throws AlertValidationException if JSON fails schema validation
     */
    public void validate(String alertId, JsonNode json) {
        log.debug("Validating alertId [{}]", alertId);

        Set<ValidationMessage> errors = compiledSchema.validate(json);

        if (!errors.isEmpty()) {
            Set<String> messages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toSet());

            log.warn("Validation failed for alertId [{}]: {}", alertId, messages);
            throw new AlertValidationException(alertId, messages);
        }

        log.debug("Validation passed for alertId [{}]", alertId);
    }

    private JsonSchema loadSchema(String schemaPath) {
        log.info("alert-util: Loading schema from [{}]", schemaPath);

        try {
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
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                "[alert-util] Failed to load schema from path [" + schemaPath + "]", e);
        }
    }
}
