package com.example.alertutil.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * All configuration for the alert-util-library.
 *
 * Consuming app must configure in application.yml:
 *
 *   alert-util:
 *     db-name: alertDb                        # REQUIRED - must match a registered datasource name
 *     view-name: v_alert_json_myapp           # REQUIRED - DB view that returns JSON
 *     alert-id-column: alert_id               # optional, default: alert_id
 *     json-column: alert_json                 # optional, default: alert_json
 *     alert-type-field: alertType             # optional, default: alertType
 *     schema-map:                             # REQUIRED - map of alertType to schema path
 *       10000: schema/schema-10000.json
 *       20000: schema/schema-20000.json
 */
@ConfigurationProperties(prefix = "alert-util")
public class AlertUtilProperties {

    /**
     * Name of the datasource/db to use for queries.
     * The library will look for a JdbcTemplate bean named {dbName}JdbcTemplate.
     *
     * Example: db-name: alertDb  →  looks for bean "alertDbJdbcTemplate"
     */
    private String dbName;

    /**
     * Name of the DB view that returns alert JSON for a given alertId.
     * The view is responsible for HTML → JSON conversion.
     */
    private String viewName;

    /**
     * Column in the view used to filter by alertId.
     */
    private String alertIdColumn = "alert_id";

    /**
     * Column in the view that holds the JSON string (can be VARCHAR or CLOB).
     */
    private String jsonColumn = "alert_json";

    /**
     * Field name inside the returned JSON that holds the alert type.
     * Used to determine which schema to validate against.
     *
     * Example: if your JSON has { "alertType": "10000", ... }  →  alertTypeField = "alertType"
     */
    private String alertTypeField = "alertType";

    /**
     * Map of alertType → classpath path to JSON schema file.
     * Schema files must be placed in the consuming app's src/main/resources.
     *
     * Example:
     *   schema-map:
     *     10000: schema/schema-10000.json
     *     20000: schema/schema-20000.json
     */
    private Map<String, String> schemaMap;

    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }

    public String getViewName() { return viewName; }
    public void setViewName(String viewName) { this.viewName = viewName; }

    public String getAlertIdColumn() { return alertIdColumn; }
    public void setAlertIdColumn(String alertIdColumn) { this.alertIdColumn = alertIdColumn; }

    public String getJsonColumn() { return jsonColumn; }
    public void setJsonColumn(String jsonColumn) { this.jsonColumn = jsonColumn; }

    public String getAlertTypeField() { return alertTypeField; }
    public void setAlertTypeField(String alertTypeField) { this.alertTypeField = alertTypeField; }

    public Map<String, String> getSchemaMap() { return schemaMap; }
    public void setSchemaMap(Map<String, String> schemaMap) { this.schemaMap = schemaMap; }
}
