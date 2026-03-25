package com.example.alertutil.service;

import com.example.alertutil.config.AlertUtilProperties.AlertTypeProperties;
import com.example.alertutil.exception.AlertProcessingException;
import com.example.alertutil.model.AlertResult;
import com.example.alertutil.repository.AlertRepository;
import com.example.alertutil.validator.JsonSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main entry point for the consuming application.
 *
 * A single AlertService bean that supports multiple databases and multiple alert types.
 * The caller supplies the dbName, alertInternalId and alertTypeId at runtime. The library
 * resolves the correct DB view and JSON schema from config for each alertTypeId.
 *
 * Usage:
 *
 *   alertService.processAlert("primaryDb", 123456L, "10000");
 *   alertService.processAlert("secondaryDb", 789012L, "20000");
 *
 * Pipeline:
 *   1. Look up alertTypeId in the configured alert-types map → resolves viewName + schemaPath
 *   2. Resolve JdbcTemplate for dbName (cached after first lookup)
 *   3. Query the resolved view by alertInternalId → returns JSON string
 *   4. Parse the JSON string into a JsonNode
 *   5. Validate against the resolved schema (lazy-loaded and cached)
 *   6. Return AlertResult to the caller
 */
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository                   alertRepository;
    private final JsonSchemaValidator               jsonSchemaValidator;
    private final ObjectMapper                      objectMapper;
    private final Map<String, AlertTypeProperties>  alertTypes;
    private final ApplicationContext                applicationContext;

    /**
     * Cache of dbName → JdbcTemplate.
     * Populated lazily on first call per dbName, reused for all subsequent calls.
     */
    private final ConcurrentHashMap<String, JdbcTemplate> jdbcTemplateCache = new ConcurrentHashMap<>();

    public AlertService(AlertRepository alertRepository,
                        JsonSchemaValidator jsonSchemaValidator,
                        ObjectMapper objectMapper,
                        Map<String, AlertTypeProperties> alertTypes) {
        this(alertRepository, jsonSchemaValidator, objectMapper, alertTypes, null);
    }

    public AlertService(AlertRepository alertRepository,
                        JsonSchemaValidator jsonSchemaValidator,
                        ObjectMapper objectMapper,
                        Map<String, AlertTypeProperties> alertTypes,
                        ApplicationContext applicationContext) {
        this.alertRepository    = alertRepository;
        this.jsonSchemaValidator = jsonSchemaValidator;
        this.objectMapper       = objectMapper;
        this.alertTypes         = alertTypes;
        this.applicationContext = applicationContext;
    }

    /**
     * Processes an alert: looks up alert-type config, queries the correct DB view,
     * parses and validates the JSON against the correct schema.
     *
     * @param dbName          name of the DataSource bean in the Spring context (e.g. "primaryDb")
     * @param alertInternalId the unique alert internal identifier (Long for DB performance)
     * @param alertTypeId     the alert type identifier used to resolve view and schema (e.g. "10000")
     * @return AlertResult containing the validated JsonNode
     *
     * @throws IllegalArgumentException if alertTypeId is not found in config
     * @throws IllegalStateException    if no DataSource bean found for dbName
     * @throws com.example.alertutil.exception.AlertNotFoundException   if no row found in DB view
     * @throws com.example.alertutil.exception.AlertValidationException if JSON fails schema validation
     * @throws com.example.alertutil.exception.AlertProcessingException if DB view returns malformed JSON
     */
    public AlertResult processAlert(String dbName, Long alertInternalId, String alertTypeId) {
        log.info("Processing alert [{}] — db: [{}], alertTypeId: [{}]", alertInternalId, dbName, alertTypeId);

        // Step 1 — resolve view name and schema path for this alert type
        AlertTypeProperties typeConfig = resolveAlertTypeConfig(alertTypeId);
        log.debug("Step 1 - Resolved alertTypeId [{}] → view: [{}], schema: [{}]",
                alertTypeId, typeConfig.getViewName(), typeConfig.getSchemaPath());

        // Step 2 — resolve JdbcTemplate (cached)
        JdbcTemplate jdbcTemplate = resolveJdbcTemplate(dbName);

        // Step 3 — query the DB view for this alert type
        log.debug("Step 3 - Fetching JSON for alertInternalId [{}] from view [{}]", alertInternalId, typeConfig.getViewName());
        String jsonString = alertRepository.fetchByAlertInternalId(jdbcTemplate, typeConfig.getViewName(), alertInternalId);

        // Step 4 — parse JSON string into JsonNode
        log.debug("Step 4 - Parsing JSON for alertInternalId [{}]", alertInternalId);
        JsonNode json = parseJson(alertInternalId, jsonString);

        // Step 5 — validate against the schema for this alert type
        log.debug("Step 5 - Validating alertInternalId [{}] against schema [{}]", alertInternalId, typeConfig.getSchemaPath());
        jsonSchemaValidator.validate(alertInternalId, typeConfig.getSchemaPath(), json);

        log.info("Alert [{}] processed successfully (alertTypeId [{}])", alertInternalId, alertTypeId);
        return new AlertResult(alertInternalId, json);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private AlertTypeProperties resolveAlertTypeConfig(String alertTypeId) {
        AlertTypeProperties config = alertTypes.get(alertTypeId);
        if (config == null) {
            throw new IllegalArgumentException(
                "\n[alert-util] No configuration found for alertTypeId [" + alertTypeId + "].\n"
                + "Register it in your application.yml:\n\n"
                + "  alert-util:\n"
                + "    alert-types:\n"
                + "      \"" + alertTypeId + "\":\n"
                + "        view-name: v_alert_" + alertTypeId + "\n"
                + "        schema-path: schema/" + alertTypeId + "_schema.json\n"
            );
        }
        return config;
    }

    /**
     * Resolves a JdbcTemplate for the given dbName.
     * On first call: looks up the DataSource bean, wraps it in a JdbcTemplate, and caches it.
     * On subsequent calls: returns the cached JdbcTemplate.
     */
    JdbcTemplate resolveJdbcTemplate(String dbName) {
        return jdbcTemplateCache.computeIfAbsent(dbName, name -> {
            log.info("alert-util: Resolving DataSource bean [{}] (first use)", name);
            try {
                DataSource dataSource = applicationContext.getBean(name, DataSource.class);
                return new JdbcTemplate(dataSource);
            } catch (Exception e) {
                throw new IllegalStateException(
                    "\n[alert-util] Could not find DataSource bean named [" + name + "].\n"
                    + "The library expects a DataSource bean with this name in your Spring context.\n\n"
                    + "Fix: Register a DataSource bean in your config class:\n\n"
                    + "  @Bean(\"" + name + "\")\n"
                    + "  @ConfigurationProperties(\"app.datasources." + name + "\")\n"
                    + "  public DataSource " + name + "DataSource() {\n"
                    + "      return DataSourceBuilder.create().build();\n"
                    + "  }\n",
                    e
                );
            }
        });
    }

    private JsonNode parseJson(Long alertInternalId, String jsonString) {
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            throw new AlertProcessingException(
                    alertInternalId, "DB view returned invalid JSON: " + e.getMessage(), e);
        }
    }
}
