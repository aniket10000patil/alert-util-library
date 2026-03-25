package com.example.alertutil.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for the alert-util-library.
 *
 * Alert type configurations (view name + schema) are loaded at startup from a DB
 * configuration table, so no per-type entries are needed in application.yml.
 *
 * Multiple alert types that share the same view and schema are grouped by a schema
 * key in the DB table, so the schema-map only needs one entry per distinct schema:
 *
 *   alert-util:
 *     view-config-db-name: configDb          # REQUIRED - DataSource bean name for config table
 *     view-config-table: alert_view_config   # REQUIRED - table: (alert_type, view_name, schema_key)
 *     schema-map:                            # REQUIRED - schemaKey → classpath schema file
 *       credit: schema/credit_schema.json
 *       equity: schema/equity_schema.json
 *
 * DB config table (example):
 *
 *   alert_type | view_name              | schema_key
 *   -----------+------------------------+-----------
 *   10000      | v_alert_json_credit    | credit
 *   10001      | v_alert_json_credit    | credit
 *   10002      | v_alert_json_credit    | credit
 *   20000      | v_alert_json_equity    | equity
 *   20001      | v_alert_json_equity    | equity
 *
 * Optional column name overrides (applied across all alert types):
 *
 *   alert-util:
 *     alert-internal-id-column: alert_internal_id   # default: alert_internal_id
 *     json-column: alert_json                       # default: alert_json
 *     alert-type-column: alert_type                 # default: alert_type
 *     view-name-column: view_name                   # default: view_name
 *     schema-key-column: schema_key                 # default: schema_key
 *
 * The dbName, alertInternalId and alertTypeId are passed at runtime:
 *
 *   alertService.processAlert("primaryDb", 123456L, "10000");
 */
@ConfigurationProperties(prefix = "alert-util")
public class AlertUtilProperties {

    /**
     * Name of the DataSource bean used to read the view config table at startup.
     * The library calls applicationContext.getBean(viewConfigDbName, DataSource.class).
     */
    private String viewConfigDbName;

    /**
     * Name of the DB table that stores alert type → view + schema_key mappings.
     * Loaded once at startup.
     */
    private String viewConfigTable;

    /**
     * Map of schemaKey → classpath path to JSON schema file.
     * Multiple alert types sharing the same schema point to the same schemaKey in the DB table,
     * so this map needs only one entry per distinct schema (not one per alert type).
     *
     * Example:
     *   schema-map:
     *     credit: schema/credit_schema.json
     *     equity: schema/equity_schema.json
     */
    private Map<String, String> schemaMap = new HashMap<>();

    /**
     * Column in the view-config-table that holds the alert type identifier.
     */
    private String alertTypeColumn = "alert_type";

    /**
     * Column in the view-config-table that holds the DB view name.
     */
    private String viewNameColumn = "view_name";

    /**
     * Column in the view-config-table that holds the schema key.
     */
    private String schemaKeyColumn = "schema_key";

    /**
     * Column in the alert views used to filter by alertInternalId.
     */
    private String alertInternalIdColumn = "alert_internal_id";

    /**
     * Column in the alert views that holds the JSON string (can be VARCHAR or CLOB).
     */
    private String jsonColumn = "alert_json";

    // -- Getters and Setters --

    public String getViewConfigDbName() { return viewConfigDbName; }
    public void setViewConfigDbName(String viewConfigDbName) { this.viewConfigDbName = viewConfigDbName; }

    public String getViewConfigTable() { return viewConfigTable; }
    public void setViewConfigTable(String viewConfigTable) { this.viewConfigTable = viewConfigTable; }

    public Map<String, String> getSchemaMap() { return schemaMap; }
    public void setSchemaMap(Map<String, String> schemaMap) { this.schemaMap = schemaMap; }

    public String getAlertTypeColumn() { return alertTypeColumn; }
    public void setAlertTypeColumn(String alertTypeColumn) { this.alertTypeColumn = alertTypeColumn; }

    public String getViewNameColumn() { return viewNameColumn; }
    public void setViewNameColumn(String viewNameColumn) { this.viewNameColumn = viewNameColumn; }

    public String getSchemaKeyColumn() { return schemaKeyColumn; }
    public void setSchemaKeyColumn(String schemaKeyColumn) { this.schemaKeyColumn = schemaKeyColumn; }

    public String getAlertInternalIdColumn() { return alertInternalIdColumn; }
    public void setAlertInternalIdColumn(String alertInternalIdColumn) { this.alertInternalIdColumn = alertInternalIdColumn; }

    public String getJsonColumn() { return jsonColumn; }
    public void setJsonColumn(String jsonColumn) { this.jsonColumn = jsonColumn; }

    /**
     * Resolved configuration for a single alert type.
     * Populated at startup by ViewConfigRepository from the DB config table.
     */
    public static class AlertTypeProperties {

        /** DB view to query for this alert type. */
        private String viewName;

        /** Classpath path to the JSON schema file (resolved from schemaKey via schema-map). */
        private String schemaPath;

        public String getViewName()  { return viewName; }
        public void setViewName(String viewName) { this.viewName = viewName; }

        public String getSchemaPath() { return schemaPath; }
        public void setSchemaPath(String schemaPath) { this.schemaPath = schemaPath; }
    }
}
