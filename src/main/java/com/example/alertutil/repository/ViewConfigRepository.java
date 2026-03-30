package com.example.alertutil.repository;

import com.example.alertutil.config.AlertUtilProperties.AlertTypeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads alert type configurations from a DB table at startup.
 *
 * Each row in the table maps an alert type to a DB view and a classpath schema path.
 * Multiple alert types can share the same view and schema path:
 *
 *   alert_type | view_name              | schema_path
 *   -----------+------------------------+------------------------------
 *   10000      | v_alert_json_credit    | schema/credit_schema.json
 *   10001      | v_alert_json_credit    | schema/credit_schema.json
 *   10002      | v_alert_json_credit    | schema/credit_schema.json
 *   20000      | v_alert_json_equity    | schema/equity_schema.json
 *
 * The schema_path column holds the full classpath path to the JSON schema file,
 * read directly — no additional YAML mapping required.
 *
 * SQL executed:
 *   SELECT {alertTypeColumn}, {viewNameColumn}, {schemaPathColumn} FROM {viewConfigTable}
 */
public class ViewConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(ViewConfigRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final String viewConfigTable;
    private final String alertTypeColumn;
    private final String viewNameColumn;
    private final String schemaPathColumn;

    public ViewConfigRepository(JdbcTemplate jdbcTemplate,
                                String viewConfigTable,
                                String alertTypeColumn,
                                String viewNameColumn,
                                String schemaPathColumn) {
        this.jdbcTemplate      = jdbcTemplate;
        this.viewConfigTable   = viewConfigTable;
        this.alertTypeColumn   = alertTypeColumn;
        this.viewNameColumn    = viewNameColumn;
        this.schemaPathColumn  = schemaPathColumn;
    }

    /**
     * Loads all alert type configurations from the DB config table.
     *
     * The schema_path column is read directly as the classpath path to the JSON schema file.
     *
     * @return map of alertTypeId → AlertTypeProperties (viewName + schemaPath)
     * @throws IllegalStateException if the config table is empty or returns null values
     */
    public Map<String, AlertTypeProperties> loadAlertTypeConfigs() {
        String sql = String.format(
                "SELECT %s, %s, %s FROM %s",
                alertTypeColumn, viewNameColumn, schemaPathColumn, viewConfigTable
        );

        log.debug("Loading alert type configs from table [{}]", viewConfigTable);

        Map<String, AlertTypeProperties> configs = new LinkedHashMap<>();

        jdbcTemplate.query(sql, (ResultSetExtractor<Void>) rs -> {
            while (rs.next()) {
                String alertType  = rs.getString(1);
                String viewName   = rs.getString(2);
                String schemaPath = rs.getString(3);

                if (alertType == null || viewName == null || schemaPath == null) {
                    log.warn("Skipping row with null value — alertType=[{}], viewName=[{}], schemaPath=[{}]",
                            alertType, viewName, schemaPath);
                    continue;
                }

                AlertTypeProperties props = new AlertTypeProperties();
                props.setViewName(viewName.trim());
                props.setSchemaPath(schemaPath.trim());
                configs.put(alertType.trim(), props);
            }
            return null;
        });

        if (configs.isEmpty()) {
            throw new IllegalStateException(
                "[alert-util] DB config table [" + viewConfigTable + "] returned no rows. "
                + "Ensure the table exists and contains at least one alert type configuration."
            );
        }

        log.info("alert-util: Loaded {} alert type config(s) from [{}]: {}",
                configs.size(), viewConfigTable, configs.keySet());
        return configs;
    }
}
