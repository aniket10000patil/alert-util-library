package com.example.alertutil.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the alert-util-library.
 *
 * Required configuration in the consuming app's application.yml:
 *
 *   alert-util:
 *     view-name: v_alert_json              # REQUIRED - DB view to query
 *     schema-base-path: schema             # REQUIRED - classpath directory containing schema files
 *
 * Schema files must be named {alertTypeId}_schema.json and placed under schema-base-path.
 * Example: src/main/resources/schema/10000_schema.json
 *
 * Optional column name overrides:
 *
 *   alert-util:
 *     alert-id-column: alert_id            # default: alert_id
 *     json-column: alert_json              # default: alert_json
 *     alert-type-id-column: alert_type_id  # default: alert_type_id
 *
 * The db-name and schema are passed at runtime to
 * alertService.processAlert(dbName, schema, alertId).
 */
@ConfigurationProperties(prefix = "alert-util")
public class AlertUtilProperties {

    /**
     * Name of the DB view that returns alert JSON for a given alertId.
     * This view is the same across all databases and environments.
     *
     * SQL executed: SELECT {jsonColumn}, {alertTypeIdColumn} FROM {schema}.{viewName} WHERE {alertIdColumn} = ?
     */
    private String viewName;

    /**
     * Classpath directory containing per-alert-type JSON schema files.
     * Schema files must be named {alertTypeId}_schema.json.
     *
     * Example: schema-base-path: schema
     * → resolves to classpath:schema/10000_schema.json for alertTypeId "10000"
     */
    private String schemaBasePath;

    /**
     * Column in the view used to filter by alertId.
     */
    private String alertIdColumn = "alert_id";

    /**
     * Column in the view that holds the JSON string (can be VARCHAR or CLOB).
     */
    private String jsonColumn = "alert_json";

    // -- Getters and Setters --

    public String getViewName() { return viewName; }
    public void setViewName(String viewName) { this.viewName = viewName; }

    public String getSchemaBasePath() { return schemaBasePath; }
    public void setSchemaBasePath(String schemaBasePath) { this.schemaBasePath = schemaBasePath; }

    public String getAlertIdColumn() { return alertIdColumn; }
    public void setAlertIdColumn(String alertIdColumn) { this.alertIdColumn = alertIdColumn; }

    public String getJsonColumn() { return jsonColumn; }
    public void setJsonColumn(String jsonColumn) { this.jsonColumn = jsonColumn; }

}
