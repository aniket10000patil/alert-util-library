package com.example.alertutil.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the alert-util-library.
 *
 * Alert type configurations (view name + schema path) are loaded at startup from a DB
 * configuration table — no per-type entries are needed in application.yml at all.
 *
 *   alert-util:
 *     view-config-db-name: configDb          # REQUIRED - DataSource bean name for config table
 *     view-config-table: alert_view_config   # REQUIRED - table: (alert_type, view_name, schema_path)
 *
 * DB config table (example):
 *
 *   alert_type | view_name              | schema_path
 *   -----------+------------------------+------------------------------
 *   10000      | v_alert_json_credit    | schema/credit_schema.json
 *   10001      | v_alert_json_credit    | schema/credit_schema.json
 *   10002      | v_alert_json_credit    | schema/credit_schema.json
 *   20000      | v_alert_json_equity    | schema/equity_schema.json
 *   20001      | v_alert_json_equity    | schema/equity_schema.json
 *
 * Optional column name overrides (applied across all alert types):
 *
 *   alert-util:
 *     alert-internal-id-column: alert_internal_id   # default: alert_internal_id
 *     json-column: alert_json                       # default: alert_json
 *     alert-type-column: alert_type                 # default: alert_type
 *     view-name-column: view_name                   # default: view_name
 *     schema-path-column: schema_path               # default: schema_path
 *
 * The dbName, alertInternalId and alertTypeId are passed at runtime:
 *
 *   alertService.processAlert("primaryDb", 123456L, "10000");
 */
@ConfigurationProperties(prefix = "alert-util")
public class AlertUtilProperties {

    /**
     * Name of the DataSource bean used to read the view config table at startup.
     */
    private String viewConfigDbName;

    /**
     * Name of the DB table that stores alert type → view + schema_path mappings.
     * Loaded once at startup.
     */
    private String viewConfigTable;

    /**
     * Column in the view-config-table that holds the alert type identifier.
     */
    private String alertTypeColumn = "alert_type";

    /**
     * Column in the view-config-table that holds the DB view name.
     */
    private String viewNameColumn = "view_name";

    /**
     * Column in the view-config-table that holds the classpath schema file path.
     */
    private String schemaPathColumn = "schema_path";

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

    public String getAlertTypeColumn() { return alertTypeColumn; }
    public void setAlertTypeColumn(String alertTypeColumn) { this.alertTypeColumn = alertTypeColumn; }

    public String getViewNameColumn() { return viewNameColumn; }
    public void setViewNameColumn(String viewNameColumn) { this.viewNameColumn = viewNameColumn; }

    public String getSchemaPathColumn() { return schemaPathColumn; }
    public void setSchemaPathColumn(String schemaPathColumn) { this.schemaPathColumn = schemaPathColumn; }

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

        /** Classpath path to the JSON schema file (read directly from the DB config table). */
        private String schemaPath;

        public String getViewName()  { return viewName; }
        public void setViewName(String viewName) { this.viewName = viewName; }

        public String getSchemaPath() { return schemaPath; }
        public void setSchemaPath(String schemaPath) { this.schemaPath = schemaPath; }
    }
}
