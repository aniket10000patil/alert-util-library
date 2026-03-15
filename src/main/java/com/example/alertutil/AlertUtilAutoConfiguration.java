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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for alert-util-library.
 *
 * Activated automatically when the library jar is on the classpath,
 * via: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 *
 * Required configuration in the consuming app's application.yml:
 *
 *   alert-util:
 *     view-name: v_alert_json                  # DB view (same across all databases)
 *     schema-path: schema/alert-schema.json    # classpath path to JSON schema
 *
 * The db-name is passed at runtime:
 *
 *   alertService.processAlert("primaryDb", "ALERT-001");
 *
 * The consuming app must register DataSource beans whose names match the
 * dbName values used at runtime.
 */
@AutoConfiguration
@EnableConfigurationProperties(AlertUtilProperties.class)
public class AlertUtilAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AlertUtilAutoConfiguration.class);

    /**
     * Loads and compiles the JSON schema at startup.
     */
    @Bean
    @ConditionalOnMissingBean
    public JsonSchemaValidator jsonSchemaValidator(AlertUtilProperties properties) {
        validateProperties(properties);
        log.info("alert-util: Initialising JsonSchemaValidator from [{}]",
                properties.getSchemaPath());
        return new JsonSchemaValidator(properties.getSchemaPath());
    }

    /**
     * Creates AlertRepository with the configured view name and column names.
     */
    @Bean
    @ConditionalOnMissingBean
    public AlertRepository alertRepository(AlertUtilProperties properties) {
        log.info("alert-util: AlertRepository configured — view: [{}], idColumn: [{}], jsonColumn: [{}]",
                properties.getViewName(), properties.getAlertIdColumn(), properties.getJsonColumn());
        return new AlertRepository(
                properties.getViewName(),
                properties.getAlertIdColumn(),
                properties.getJsonColumn()
        );
    }

    /**
     * Creates the main AlertService.
     * DataSource resolution happens lazily at runtime when processAlert() is called.
     */
    @Bean
    @ConditionalOnMissingBean
    public AlertService alertService(AlertRepository alertRepository,
                                     JsonSchemaValidator jsonSchemaValidator,
                                     ObjectMapper objectMapper,
                                     ApplicationContext applicationContext) {
        log.info("alert-util: AlertService ready");
        return new AlertService(alertRepository, jsonSchemaValidator, objectMapper, applicationContext);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateProperties(AlertUtilProperties properties) {
        StringBuilder errors = new StringBuilder();

        if (isBlank(properties.getViewName()))
            errors.append("\n  - alert-util.view-name is required");

        if (isBlank(properties.getSchemaPath()))
            errors.append("\n  - alert-util.schema-path is required");

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                "\n[alert-util] Missing required configuration:" + errors
                + "\n\nAdd the following to your application.yml:\n\n"
                + "  alert-util:\n"
                + "    view-name: v_alert_json\n"
                + "    schema-path: schema/alert-schema.json\n"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
