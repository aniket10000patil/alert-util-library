package com.example.alertutil.service;

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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main entry point for the consuming application.
 *
 * A single AlertService bean that can work with multiple databases.
 * The caller passes only the dbName at runtime — the view name is
 * configured once in application.yml and is the same across all databases.
 *
 * Usage:
 *
 *   alertService.processAlert("primaryDb",   "mySchema", "ALERT-001", "10000");
 *   alertService.processAlert("secondaryDb", "mySchema", "ALERT-002", "20000");
 *
 * Pipeline:
 *   1. Resolve JdbcTemplate for the given dbName (cached after first lookup)
 *   2. Query the configured view by alertId → returns JSON string
 *   3. Parse the JSON string into a JsonNode
 *   4. Validate against the schema for the given alertTypeId (lazy-loaded and cached)
 *   5. Return AlertResult to the caller
 */
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository      alertRepository;
    private final JsonSchemaValidator   jsonSchemaValidator;
    private final ObjectMapper          objectMapper;
    private final ApplicationContext    applicationContext;

    /**
     * Cache of dbName → JdbcTemplate.
     * Populated lazily on first call per dbName, reused for all subsequent calls.
     */
    private final ConcurrentHashMap<String, JdbcTemplate> jdbcTemplateCache = new ConcurrentHashMap<>();

    public AlertService(AlertRepository alertRepository,
                        JsonSchemaValidator jsonSchemaValidator,
                        ObjectMapper objectMapper) {
        this(alertRepository, jsonSchemaValidator, objectMapper, null);
    }

    public AlertService(AlertRepository alertRepository,
                        JsonSchemaValidator jsonSchemaValidator,
                        ObjectMapper objectMapper,
                        ApplicationContext applicationContext) {
        this.alertRepository     = alertRepository;
        this.jsonSchemaValidator  = jsonSchemaValidator;
        this.objectMapper        = objectMapper;
        this.applicationContext  = applicationContext;
    }

    /**
     * Processes an alert: resolves the database by name, queries the configured view,
     * parses and validates the JSON.
     *
     * @param dbName      name of the DataSource bean in the Spring context (e.g. "primaryDb")
     * @param schema      database schema name used to qualify the view (e.g. "mySchema")
     * @param alertId     the unique alert identifier
     * @param alertTypeId the alert type identifier used to select the JSON schema (e.g. "10000")
     * @return AlertResult containing the validated JsonNode
     *
     * @throws IllegalStateException if no DataSource bean found for dbName
     * @throws com.example.alertutil.exception.AlertNotFoundException   if no row found in DB view
     * @throws com.example.alertutil.exception.AlertValidationException if JSON fails schema validation
     * @throws com.example.alertutil.exception.AlertProcessingException if DB view returns malformed JSON
     */
    public AlertResult processAlert(String dbName, String schema, String alertId, String alertTypeId) {
        log.info("Processing alert [{}] — db: [{}], schema: [{}], alertTypeId: [{}]", alertId, dbName, schema, alertTypeId);

        // Step 1 — resolve JdbcTemplate (cached)
        JdbcTemplate jdbcTemplate = resolveJdbcTemplate(dbName);

        // Step 2 — query the DB view
        log.debug("Step 2 - Fetching JSON for alertId [{}]", alertId);
        String jsonString = alertRepository.fetchByAlertId(jdbcTemplate, schema, alertId);

        // Step 3 — parse JSON string into JsonNode
        log.debug("Step 3 - Parsing JSON for alertId [{}]", alertId);
        JsonNode json = parseJson(alertId, jsonString);

        // Step 4 — validate against the schema for the given alertTypeId
        log.debug("Step 4 - Validating alertId [{}] against alertTypeId [{}]", alertId, alertTypeId);
        jsonSchemaValidator.validate(alertId, alertTypeId, json);

        log.info("Alert [{}] processed successfully", alertId);
        return new AlertResult(alertId, json);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a JdbcTemplate for the given dbName.
     *
     * On first call: looks up the DataSource bean from ApplicationContext, wraps it
     * in a JdbcTemplate, and caches it.
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

    private JsonNode parseJson(String alertId, String jsonString) {
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            throw new AlertProcessingException(
                    alertId, "DB view returned invalid JSON: " + e.getMessage(), e);
        }
    }
}
