package com.example.alertutil.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bind these in the consuming app's application.yml:
 *
 * alert-util:
 *   view-name: v_alert_json_myapp              # REQUIRED — DB view for this app
 *   schema-path: schema/my-alert-schema.json   # REQUIRED — classpath path in consuming app
 *   alert-id-column: alert_id                  # default, override if different
 *   json-column: alert_json                    # default, override if different
 */
@ConfigurationProperties(prefix = "alert-util")
public class AlertUtilProperties {

    /**
     * Name of the DB view that returns JSON for a given alertId.
     * REQUIRED — must be configured by the consuming application.
     */
    private String viewName;

    /**
     * Classpath path to the JSON schema file.
     * REQUIRED — schema must live in the consuming application's src/main/resources.
     *
     * Example: schema/my-alert-schema.json
     * Maps to: src/main/resources/schema/my-alert-schema.json in the consuming app.
     */
    private String schemaPath;

    /** Column in the view used to filter by alertId */
    private String alertIdColumn = "alert_id";

    /** Column in the view that holds the converted JSON string */
    private String jsonColumn = "alert_json";

    public String getViewName() { return viewName; }
    public void setViewName(String viewName) { this.viewName = viewName; }

    public String getSchemaPath() { return schemaPath; }
    public void setSchemaPath(String schemaPath) { this.schemaPath = schemaPath; }

    public String getAlertIdColumn() { return alertIdColumn; }
    public void setAlertIdColumn(String alertIdColumn) { this.alertIdColumn = alertIdColumn; }

    public String getJsonColumn() { return jsonColumn; }
    public void setJsonColumn(String jsonColumn) { this.jsonColumn = jsonColumn; }
}
