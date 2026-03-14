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
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Auto-configuration for alert-util-library.
 *
 * Activated automatically when the library jar is on the classpath,
 * via: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 *
 * Required configuration in the consuming app's application.yml:
 *
 *   alert-util:
 *     db-name: alertDb                         # name of a DataSource bean in the app context
 *     view-name: v_alert_json_myapp            # DB view to query
 *     schema-path: schema/alert-schema.json    # classpath path to JSON schema
 *
 * The consuming app must have a DataSource bean whose name matches db-name.
 * For example, if using Spring's default single-datasource setup:
 *
 *   @Bean("alertDb")
 *   @ConfigurationProperties("spring.datasource")
 *   public DataSource alertDbDataSource() { ... }
 *
 * Or in a multi-datasource setup, any bean named "alertDb" that is a DataSource.
 */
@AutoConfiguration
@EnableConfigurationProperties(AlertUtilProperties.class)
public class AlertUtilAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AlertUtilAutoConfiguration.class);

    /**
     * Loads and compiles the JSON schema at startup.
     * Fails fast if the schema file is missing or not configured.
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
     * Creates AlertRepository by resolving the DataSource bean from the app context.
     *
     * The library creates its own JdbcTemplate from the resolved DataSource.
     * This way the consuming app only needs to define a DataSource bean — no need
     * to manually register a JdbcTemplate for the library.
     *
     * e.g. alert-util.db-name=alertDb → looks for a DataSource bean named "alertDb"
     */
    @Bean
    @ConditionalOnMissingBean
    public AlertRepository alertRepository(AlertUtilProperties properties,
                                           ApplicationContext context) {
        String dbName = properties.getDbName();
        log.info("alert-util: Resolving DataSource bean [{}]", dbName);

        DataSource dataSource = resolveDataSource(context, dbName);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        log.info("alert-util: AlertRepository configured — db: [{}], view: [{}]",
                dbName, properties.getViewName());
        return new AlertRepository(jdbcTemplate, properties);
    }

    /**
     * Creates the main AlertService that the consuming app injects and uses.
     */
    @Bean
    @ConditionalOnMissingBean
    public AlertService alertService(AlertRepository alertRepository,
                                     JsonSchemaValidator jsonSchemaValidator,
                                     ObjectMapper objectMapper) {
        log.info("alert-util: AlertService ready");
        return new AlertService(alertRepository, jsonSchemaValidator, objectMapper);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Looks up the DataSource by bean name from the ApplicationContext.
     * Throws a clear, actionable error if it cannot be found.
     */
    private DataSource resolveDataSource(ApplicationContext context, String dbName) {
        try {
            return context.getBean(dbName, DataSource.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                "\n[alert-util] Could not find DataSource bean named [" + dbName + "].\n"
                + "You set alert-util.db-name=" + dbName + "\n"
                + "The library expects a DataSource bean with this name in your Spring context.\n\n"
                + "Fix: Register a DataSource bean in your config class:\n\n"
                + "  @Bean(\"" + dbName + "\")\n"
                + "  @ConfigurationProperties(\"spring.datasource\")\n"
                + "  public DataSource " + dbName + "DataSource() {\n"
                + "      return DataSourceBuilder.create().build();\n"
                + "  }\n\n"
                + "Or if you have a multi-datasource setup, ensure one of your\n"
                + "DataSource beans is named [" + dbName + "].\n",
                e
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

        if (isBlank(properties.getViewName()))
            errors.append("\n  - alert-util.view-name is required");

        if (isBlank(properties.getSchemaPath()))
            errors.append("\n  - alert-util.schema-path is required");

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                "\n[alert-util] Missing required configuration:" + errors
                + "\n\nAdd the following to your application.yml:\n\n"
                + "  alert-util:\n"
                + "    db-name: alertDb                      # name of your DataSource bean\n"
                + "    view-name: v_alert_json_myapp          # DB view to query\n"
                + "    schema-path: schema/alert-schema.json  # classpath path to JSON schema\n"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
