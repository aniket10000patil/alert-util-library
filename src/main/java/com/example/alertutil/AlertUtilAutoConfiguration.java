package com.example.alertutil;

import com.example.alertutil.config.AlertUtilProperties;
import com.example.alertutil.config.AlertUtilProperties.AlertTypeProperties;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for alert-util-library.
 *
 * Activated automatically when the library jar is on the classpath,
 * via: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 *
 * Required configuration in the consuming app's application.yml:
 *
 *   alert-util:
 *     alert-types:
 *       "10000":
 *         view-name: v_alert_10000
 *         schema-path: schema/10000_schema.json
 *       "20000":
 *         view-name: v_alert_20000
 *         schema-path: schema/20000_schema.json
 *
 * The dbName and alertTypeId are passed at runtime:
 *
 *   alertService.processAlert("primaryDb", "ALERT-001", "10000");
 */
@AutoConfiguration
@EnableConfigurationProperties(AlertUtilProperties.class)
public class AlertUtilAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AlertUtilAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public JsonSchemaValidator jsonSchemaValidator() {
        log.info("alert-util: Initialising JsonSchemaValidator (schemas loaded lazily per alert type)");
        return new JsonSchemaValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertRepository alertRepository(AlertUtilProperties properties) {
        log.info("alert-util: AlertRepository configured — idColumn: [{}], jsonColumn: [{}]",
                properties.getAlertIdColumn(), properties.getJsonColumn());
        return new AlertRepository(
                properties.getAlertIdColumn(),
                properties.getJsonColumn()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertService alertService(AlertRepository alertRepository,
                                     JsonSchemaValidator jsonSchemaValidator,
                                     ObjectMapper objectMapper,
                                     AlertUtilProperties properties,
                                     ApplicationContext applicationContext) {
        validateProperties(properties);
        log.info("alert-util: AlertService ready — {} alert type(s) configured: {}",
                properties.getAlertTypes().size(), properties.getAlertTypes().keySet());
        return new AlertService(alertRepository, jsonSchemaValidator, objectMapper,
                properties.getAlertTypes(), applicationContext);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateProperties(AlertUtilProperties properties) {
        List<String> errors = new ArrayList<>();

        if (properties.getAlertTypes() == null || properties.getAlertTypes().isEmpty()) {
            errors.add("alert-util.alert-types must have at least one entry");
        } else {
            for (Map.Entry<String, AlertTypeProperties> entry : properties.getAlertTypes().entrySet()) {
                String typeId = entry.getKey();
                AlertTypeProperties type = entry.getValue();

                if (isBlank(type.getViewName()))
                    errors.add("alert-util.alert-types." + typeId + ".view-name is required");

                if (isBlank(type.getSchemaPath()))
                    errors.add("alert-util.alert-types." + typeId + ".schema-path is required");
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                "\n[alert-util] Invalid configuration:\n  - "
                + String.join("\n  - ", errors)
                + "\n\nExample configuration:\n\n"
                + "  alert-util:\n"
                + "    alert-types:\n"
                + "      \"10000\":\n"
                + "        view-name: v_alert_10000\n"
                + "        schema-path: schema/10000_schema.json\n"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
