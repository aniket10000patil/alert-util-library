package com.example.alertutil;

import com.example.alertutil.config.AlertUtilProperties;
import com.example.alertutil.config.AlertUtilProperties.AlertTypeProperties;
import com.example.alertutil.repository.AlertRepository;
import com.example.alertutil.repository.ViewConfigRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
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
 *     view-config-db-name: configDb          # DataSource bean that holds the config table
 *     view-config-table: alert_view_config   # table: (alert_type, view_name, schema_key)
 *     schema-map:
 *       credit: schema/credit_schema.json
 *       equity: schema/equity_schema.json
 *
 * At startup the library reads all rows from the view config table and builds an in-memory
 * map of alertTypeId → (viewName, schemaPath). At runtime the caller passes the alertTypeId:
 *
 *   alertService.processAlert("primaryDb", 123456L, "10000");
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
                properties.getAlertInternalIdColumn(), properties.getJsonColumn());
        return new AlertRepository(
                properties.getAlertInternalIdColumn(),
                properties.getJsonColumn()
        );
    }

    /**
     * Creates the main AlertService.
     *
     * Alert type configurations are loaded from the DB config table at startup using
     * the DataSource bean named by alert-util.view-config-db-name. The resulting
     * alertType → (viewName, schemaPath) map is passed to AlertService, which uses it
     * to resolve the correct view and schema on each processAlert call.
     */
    @Bean
    @ConditionalOnMissingBean
    public AlertService alertService(AlertRepository alertRepository,
                                     JsonSchemaValidator jsonSchemaValidator,
                                     ObjectMapper objectMapper,
                                     AlertUtilProperties properties,
                                     ApplicationContext applicationContext) {
        validateProperties(properties);

        Map<String, AlertTypeProperties> alertTypes = loadAlertTypesFromDb(properties, applicationContext);

        log.info("alert-util: AlertService ready — {} alert type(s) loaded from DB config table [{}]: {}",
                alertTypes.size(), properties.getViewConfigTable(), alertTypes.keySet());

        return new AlertService(alertRepository, jsonSchemaValidator, objectMapper,
                alertTypes, applicationContext);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the DataSource bean for the config table, creates a ViewConfigRepository,
     * and loads all alert type configs from the DB.
     */
    private Map<String, AlertTypeProperties> loadAlertTypesFromDb(AlertUtilProperties properties,
                                                                    ApplicationContext context) {
        String beanName = properties.getViewConfigDbName();
        log.info("alert-util: Loading view config from table [{}] using DataSource bean [{}]",
                properties.getViewConfigTable(), beanName);

        DataSource dataSource = resolveDataSource(context, beanName);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        ViewConfigRepository viewConfigRepository = new ViewConfigRepository(
                jdbcTemplate,
                properties.getViewConfigTable(),
                properties.getAlertTypeColumn(),
                properties.getViewNameColumn(),
                properties.getSchemaKeyColumn()
        );

        return viewConfigRepository.loadAlertTypeConfigs(properties.getSchemaMap());
    }

    private DataSource resolveDataSource(ApplicationContext context, String beanName) {
        try {
            return context.getBean(beanName, DataSource.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                "\n[alert-util] Could not find DataSource bean named [" + beanName + "].\n"
                + "You set alert-util.view-config-db-name=" + beanName + "\n"
                + "The library expects a DataSource bean with this name to be registered "
                + "in the consuming application.\n\n"
                + "Fix: Register a DataSource bean in your config class:\n\n"
                + "  @Bean(\"" + beanName + "\")\n"
                + "  @ConfigurationProperties(\"app.datasources." + beanName + "\")\n"
                + "  public DataSource " + beanName + "() {\n"
                + "      return DataSourceBuilder.create().build();\n"
                + "  }\n",
                e
            );
        }
    }

    private void validateProperties(AlertUtilProperties properties) {
        List<String> errors = new ArrayList<>();

        if (isBlank(properties.getViewConfigDbName()))
            errors.add("alert-util.view-config-db-name is required");

        if (isBlank(properties.getViewConfigTable()))
            errors.add("alert-util.view-config-table is required");

        if (properties.getSchemaMap() == null || properties.getSchemaMap().isEmpty())
            errors.add("alert-util.schema-map must have at least one entry");

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                "\n[alert-util] Missing required configuration:\n  - "
                + String.join("\n  - ", errors)
                + "\n\nExample configuration:\n\n"
                + "  alert-util:\n"
                + "    view-config-db-name: configDb\n"
                + "    view-config-table: alert_view_config\n"
                + "    schema-map:\n"
                + "      credit: schema/credit_schema.json\n"
                + "      equity: schema/equity_schema.json\n"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
