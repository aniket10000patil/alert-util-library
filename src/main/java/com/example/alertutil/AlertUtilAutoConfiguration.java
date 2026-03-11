package com.example.alertutil;

import com.example.alertutil.config.AlertUtilProperties;
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

import java.util.Collections;
import java.util.Map;

/**
 * Auto-configuration for alert-util-library.
 *
 * Activated automatically when the library jar is on the classpath,
 * via: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 *
 * Minimal required configuration in application.yml:
 *
 *   alert-util:
 *     db-name: alertDb                         # REQUIRED
 *     view-name: v_alert_json_myapp            # required unless view-config-table is set
 *     schema-map:                              # REQUIRED
 *       10000: schema/schema-10000.json
 *       20000: schema/schema-20000.json
 *
 * To drive view selection from a DB config table (multiple views):
 *
 *   alert-util:
 *     db-name: alertDb
 *     view-config-table: alert_view_config     # DB table: (alert_type, view_name)
 *     view-key-column: alert_type              # optional, default: alert_type
 *     view-name-column: view_name              # optional, default: view_name
 *     schema-map:
 *       10000: schema/schema-10000.json
 *       20000: schema/schema-20000.json
 *
 * The consuming app MUST register a JdbcTemplate bean named {dbName}JdbcTemplate.
 * e.g. for db-name=alertDb → register a bean named "alertDbJdbcTemplate"
 */
@AutoConfiguration
@EnableConfigurationProperties(AlertUtilProperties.class)
public class AlertUtilAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AlertUtilAutoConfiguration.class);

    /**
     * Loads and compiles all JSON schemas at startup.
     * Fails fast if any schema file is missing or schema-map is not configured.
     */
    @Bean
    @ConditionalOnMissingBean
    public JsonSchemaValidator jsonSchemaValidator(AlertUtilProperties properties) {
        validateProperties(properties);
        log.info("alert-util: Initialising JsonSchemaValidator for types: {}",
                properties.getSchemaMap().keySet());
        return new JsonSchemaValidator(
                properties.getSchemaMap(),
                properties.getAlertTypeField()
        );
    }

    /**
     * Creates AlertRepository using the JdbcTemplate resolved from ApplicationContext
     * by the name: {dbName}JdbcTemplate.
     *
     * e.g. alert-util.db-name=alertDb → looks for bean "alertDbJdbcTemplate"
     *
     * This allows the consuming app to have multiple datasources and tell the library
     * exactly which one to use, just by setting db-name in application.yml.
     */
    @Bean
    @ConditionalOnMissingBean
    public AlertRepository alertRepository(AlertUtilProperties properties,
                                           ApplicationContext context) {
        String jdbcBeanName = properties.getDbName() + "JdbcTemplate";
        log.info("alert-util: Resolving JdbcTemplate bean [{}] for db [{}]",
                jdbcBeanName, properties.getDbName());

        JdbcTemplate jdbcTemplate = resolveJdbcTemplate(context, jdbcBeanName, properties.getDbName());

        log.info("alert-util: AlertRepository configured — db: [{}]", properties.getDbName());
        return new AlertRepository(jdbcTemplate, properties);
    }

    /**
     * Creates the main AlertService that the consuming app injects and uses.
     *
     * If alert-util.view-config-table is configured, loads view mappings from that
     * DB table at startup and makes them available via processAlert(alertId, viewKey).
     */
    @Bean
    @ConditionalOnMissingBean
    public AlertService alertService(AlertRepository alertRepository,
                                     JsonSchemaValidator jsonSchemaValidator,
                                     ObjectMapper objectMapper,
                                     AlertUtilProperties properties,
                                     ApplicationContext context) {

        Map<String, String> viewConfigMap = loadViewConfigMap(properties, context);

        log.info("alert-util: AlertService ready — defaultView: [{}], dbConfigViews: {}",
                properties.getViewName(),
                viewConfigMap.isEmpty() ? "none" : viewConfigMap.keySet());

        return new AlertService(
                alertRepository,
                jsonSchemaValidator,
                objectMapper,
                properties.getViewName(),
                viewConfigMap
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * If view-config-table is configured, loads the key→view_name map from the DB.
     * Returns an empty map when no view-config-table is set.
     */
    private Map<String, String> loadViewConfigMap(AlertUtilProperties properties,
                                                   ApplicationContext context) {
        if (isBlank(properties.getViewConfigTable())) {
            return Collections.emptyMap();
        }

        String jdbcBeanName = properties.getDbName() + "JdbcTemplate";
        JdbcTemplate jdbcTemplate = resolveJdbcTemplate(context, jdbcBeanName, properties.getDbName());

        ViewConfigRepository viewConfigRepository = new ViewConfigRepository(
                jdbcTemplate,
                properties.getViewConfigTable(),
                properties.getViewKeyColumn(),
                properties.getViewNameColumn()
        );

        log.info("alert-util: Loading view config from table [{}] (key: {}, view: {})",
                properties.getViewConfigTable(),
                properties.getViewKeyColumn(),
                properties.getViewNameColumn());

        return viewConfigRepository.loadViewConfig();
    }

    /**
     * Looks up the JdbcTemplate by bean name from the ApplicationContext.
     * Throws a clear, actionable error if it cannot be found.
     */
    private JdbcTemplate resolveJdbcTemplate(ApplicationContext context,
                                              String beanName,
                                              String dbName) {
        try {
            return context.getBean(beanName, JdbcTemplate.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                "\n[alert-util] Could not find JdbcTemplate bean named [" + beanName + "].\n"
                + "You set alert-util.db-name=" + dbName + "\n"
                + "The library expects a JdbcTemplate bean named [" + beanName + "] "
                + "to be registered in the consuming application.\n\n"
                + "Fix: Register this bean in your DataSource config class:\n\n"
                + "  @Bean\n"
                + "  public JdbcTemplate " + beanName + "(\n"
                + "          @Qualifier(\"" + dbName + "DataSource\") DataSource dataSource) {\n"
                + "      return new JdbcTemplate(dataSource);\n"
                + "  }\n\n"
                + "Or if using MultiDataSourceConfig, ensure 'app.datasources." + dbName + "' is configured."
            );
        }
    }

    /**
     * Validates all required properties are present.
     * Fails at startup with a clear message listing everything missing.
     */
    private void validateProperties(AlertUtilProperties properties) {
        StringBuilder errors = new StringBuilder();

        if (isBlank(properties.getDbName()))
            errors.append("\n  - alert-util.db-name is required");

        if (isBlank(properties.getViewName()) && isBlank(properties.getViewConfigTable()))
            errors.append("\n  - either alert-util.view-name or alert-util.view-config-table is required");

        if (properties.getSchemaMap() == null || properties.getSchemaMap().isEmpty())
            errors.append("\n  - alert-util.schema-map is required (at least one entry)");

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                "\n[alert-util] Missing required configuration:" + errors
                + "\n\nAdd the following to your application.yml:\n\n"
                + "  alert-util:\n"
                + "    db-name: alertDb\n"
                + "    view-name: v_alert_json_myapp        # or use view-config-table\n"
                + "    alert-type-field: alertType\n"
                + "    schema-map:\n"
                + "      10000: schema/schema-10000.json\n"
                + "      20000: schema/schema-20000.json\n"
                + "\n"
                + "Or for DB-driven multi-view config:\n\n"
                + "  alert-util:\n"
                + "    db-name: alertDb\n"
                + "    view-config-table: alert_view_config\n"
                + "    schema-map:\n"
                + "      10000: schema/schema-10000.json\n"
                + "      20000: schema/schema-20000.json\n"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
