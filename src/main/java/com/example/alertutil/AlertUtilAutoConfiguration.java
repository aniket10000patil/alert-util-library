package com.example.alertutil;

import com.example.alertutil.config.AlertUtilProperties;
import com.example.alertutil.repository.AlertRepository;
import com.example.alertutil.service.AlertService;
import com.example.alertutil.validator.JsonSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration for the alert-util-library.
 *
 * The consuming app MUST configure in application.yml:
 *   alert-util:
 *     view-name: v_alert_json_myapp              # REQUIRED
 *     schema-path: schema/my-alert-schema.json   # REQUIRED — file in consuming app's classpath
 *     alert-id-column: alert_id                  # optional, default: alert_id
 *     json-column: alert_json                    # optional, default: alert_json
 */
@AutoConfiguration
@EnableConfigurationProperties(AlertUtilProperties.class)
@ComponentScan(basePackages = "com.example.alertutil")
public class AlertUtilAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AlertUtilAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public JsonSchemaValidator jsonSchemaValidator(AlertUtilProperties properties) {
        validateRequiredProperties(properties);
        log.info("alert-util: Loading JSON schema from consuming app classpath [{}]", properties.getSchemaPath());
        return new JsonSchemaValidator(properties.getSchemaPath());
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertRepository alertRepository(JdbcTemplate jdbcTemplate,
                                           AlertUtilProperties properties) {
        log.info("alert-util: Configuring AlertRepository with view [{}]", properties.getViewName());
        return new AlertRepository(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertService alertService(AlertRepository alertRepository,
                                     JsonSchemaValidator jsonSchemaValidator,
                                     ObjectMapper objectMapper) {
        log.info("alert-util: AlertService initialised");
        return new AlertService(alertRepository, jsonSchemaValidator, objectMapper);
    }

    /**
     * Fail fast at startup if required properties are missing,
     * with a clear actionable message pointing to what the consuming app must configure.
     */
    private void validateRequiredProperties(AlertUtilProperties properties) {
        StringBuilder errors = new StringBuilder();

        if (isBlank(properties.getViewName())) {
            errors.append("\n  - 'alert-util.view-name' is required. Set it to your app-specific DB view name.");
        }
        if (isBlank(properties.getSchemaPath())) {
            errors.append("\n  - 'alert-util.schema-path' is required. Point it to your JSON schema file on the classpath.");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                "[alert-util] Missing required configuration. Add the following to your application.yml:"
                + errors
                + "\n\nExample:"
                + "\n  alert-util:"
                + "\n    view-name: v_alert_json_myapp"
                + "\n    schema-path: schema/my-alert-schema.json"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
