package com.example.alertutil.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the alert-util-library.
 *
 * Each alert type must be registered under alert-util.alert-types with its own
 * DB view name and JSON schema path:
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
 * Optional column name overrides (applied across all alert types):
 *
 *   alert-util:
 *     alert-id-column: alert_id   # default: alert_id
 *     json-column: alert_json     # default: alert_json
 *
 * The db-name and alert-type-id are passed at runtime to
 * alertService.processAlert(dbName, alertId, alertTypeId).
 */
@ConfigurationProperties(prefix = "alert-util")
public class AlertUtilProperties {

    /**
     * Per-alert-type configuration keyed by alertTypeId.
     * Each entry defines the DB view and JSON schema to use for that alert type.
     */
    private Map<String, AlertTypeProperties> alertTypes = new HashMap<>();

    /**
     * Column in the view used to filter by alertId.
     */
    private String alertIdColumn = "alert_id";

    /**
     * Column in the view that holds the JSON string (can be VARCHAR or CLOB).
     */
    private String jsonColumn = "alert_json";

    // -- Getters and Setters --

    public Map<String, AlertTypeProperties> getAlertTypes() { return alertTypes; }
    public void setAlertTypes(Map<String, AlertTypeProperties> alertTypes) { this.alertTypes = alertTypes; }

    public String getAlertIdColumn() { return alertIdColumn; }
    public void setAlertIdColumn(String alertIdColumn) { this.alertIdColumn = alertIdColumn; }

    public String getJsonColumn() { return jsonColumn; }
    public void setJsonColumn(String jsonColumn) { this.jsonColumn = jsonColumn; }

    /**
     * Configuration for a single alert type.
     */
    public static class AlertTypeProperties {

        /**
         * DB view to query for this alert type.
         * Can include a schema qualifier if needed (e.g. myschema.v_alert_10000).
         */
        private String viewName;

        /**
         * Classpath path to the JSON schema file for this alert type.
         * Example: schema/10000_schema.json
         */
        private String schemaPath;

        public String getViewName() { return viewName; }
        public void setViewName(String viewName) { this.viewName = viewName; }

        public String getSchemaPath() { return schemaPath; }
        public void setSchemaPath(String schemaPath) { this.schemaPath = schemaPath; }
    }
}
