package com.example.alertutil.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the alert-util-library.
 *
 * Required configuration in the consuming app's application.yml:
 *
 *   alert-util:
 *     db-name: alertDb                        # REQUIRED - name of a DataSource bean defined in the app
 *     view-name: v_alert_json_myapp           # REQUIRED - DB view to query for alert JSON
 *     schema-path: schema/alert-schema.json   # REQUIRED - classpath path to the JSON schema
 *
 * The library resolves the DataSource from the consuming app's Spring context
 * by bean name. The consuming app owns all DB connection config in its own
 * application.yml (url, username, password, HikariCP settings etc.).
 *
 * Optional properties for column name overrides:
 *
 *   alert-util:
 *     alert-id-column: alert_id               # optional, default: alert_id
 *     json-column: alert_json                  # optional, default: alert_json
 */
@ConfigurationProperties(prefix = "alert-util")
public class AlertUtilProperties {

    /**
     * Name of the DataSource bean registered in the consuming application.
     * The library looks up this bean from the Spring ApplicationContext.
     *
     * Example: db-name: alertDb  →  looks for a DataSource bean named "alertDb"
     */
    private String dbName;

    /**
     * Name of the DB view that returns alert JSON for a given alertId.
     *
     * SQL executed: SELECT {jsonColumn} FROM {viewName} WHERE {alertIdColumn} = ?
     */
    private String viewName;

    /**
     * Classpath path to the JSON schema file used for validation.
     * The file must be placed in the consuming app's src/main/resources.
     *
     * Example: schema-path: schema/alert-schema.json
     */
    private String schemaPath;

    /**
     * Column in the view used to filter by alertId.
     */
    private String alertIdColumn = "alert_id";

    /**
     * Column in the view that holds the JSON string (can be VARCHAR or CLOB).
     */
    private String jsonColumn = "alert_json";

    // -- Getters and Setters --

    public String getDbName() { return dbName; }
    public void setDbName(String dbName) { this.dbName = dbName; }

    public String getViewName() { return viewName; }
    public void setViewName(String viewName) { this.viewName = viewName; }

    public String getSchemaPath() { return schemaPath; }
    public void setSchemaPath(String schemaPath) { this.schemaPath = schemaPath; }

    public String getAlertIdColumn() { return alertIdColumn; }
    public void setAlertIdColumn(String alertIdColumn) { this.alertIdColumn = alertIdColumn; }

    public String getJsonColumn() { return jsonColumn; }
    public void setJsonColumn(String jsonColumn) { this.jsonColumn = jsonColumn; }
}
