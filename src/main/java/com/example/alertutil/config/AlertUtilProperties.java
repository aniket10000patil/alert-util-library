package com.example.alertutil.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration for the alert-util-library.
 *
 * Only TWO properties are required from the consuming app:
 *
 *   alert-util:
 *     db-name: alertDb                        # REQUIRED - name of a DataSource bean defined in the app
 *     view-name: v_alert_json_myapp           # REQUIRED - DB view to query for alert JSON
 *
 * The library resolves the DataSource from the consuming app's Spring context
 * by bean name. The consuming app owns all DB connection config in its own
 * application.yml (url, username, password, HikariCP settings etc.).
 *
 * Additional (optional) properties for JSON column handling and schema validation:
 *
 *   alert-util:
 *     db-name: alertDb
 *     view-name: v_alert_json_myapp
 *     alert-id-column: alert_id               # optional, default: alert_id
 *     json-column: alert_json                  # optional, default: alert_json
 *     alert-type-field: alertType              # optional, default: alertType
 *     schema-map:                              # REQUIRED - map of alertType → schema classpath
 *       10000: schema/schema-10000.json
 *       20000: schema/schema-20000.json
 */
@ConfigurationProperties(prefix = "alert-util")
public class AlertUtilProperties {

    /**
     * Name of the DataSource bean registered in the consuming application.
     * The library looks up this bean from the Spring ApplicationContext.
     *
     * Example: db-name: alertDb  →  looks for a DataSource bean named "alertDb"
     *
     * The consuming app configures the actual connection details in its own
     * application.yml under whatever datasource config it uses.
     */
    private String dbName;

    /**
     * Name of the DB view that returns alert JSON for a given alertId.
     * The view is responsible for HTML → JSON conversion (if applicable).
     *
     * SQL executed: SELECT {jsonColumn} FROM {viewName} WHERE {alertIdColumn} = ?
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
     */
    private String alertTypeField = "alertType";

    /**
     * Map of alertType → classpath path to JSON schema file.
     * Schema files must be placed in the consuming app's src/main/resources.
     */
    private Map<String, String> schemaMap;

    // -- Getters and Setters --

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
