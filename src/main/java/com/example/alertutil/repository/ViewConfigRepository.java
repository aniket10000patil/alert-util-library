package com.example.alertutil.repository;

import com.example.alertutil.config.AlertUtilProperties;
import com.example.alertutil.config.AlertUtilProperties.AlertTypeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads alert type configurations from a DB table at startup.
 *
 * Each row in the table maps an alert type to a DB view and a schema key.
 * Multiple alert types can share the same view and schema key:
 *
 *   alert_type | view_name              | schema_key
 *   -----------+------------------------+-----------
 *   10000      | v_alert_json_credit    | credit
 *   10001      | v_alert_json_credit    | credit
 *   10002      | v_alert_json_credit    | credit
 *   20000      | v_alert_json_equity    | equity
 *
 * The schemaKey is resolved to a classpath schema path via the schema-map in
 * application.yml, so only one schema-map entry is needed per distinct schema.
 *
 * SQL executed:
 *   SELECT {alertTypeColumn}, {viewNameColumn}, {schemaKeyColumn} FROM {viewConfigTable}
 */
public class ViewConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(ViewConfigRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final String viewConfigTable;
    private final String alertTypeColumn;
    private final String viewNameColumn;
    private final String schemaKeyColumn;

    public ViewConfigRepository(JdbcTemplate jdbcTemplate,
                                String viewConfigTable,
                                String alertTypeColumn,
                                String viewNameColumn,
                                String schemaKeyColumn) {
        this.jdbcTemplate     = jdbcTemplate;
        this.viewConfigTable  = viewConfigTable;
        this.alertTypeColumn  = alertTypeColumn;
        this.viewNameColumn   = viewNameColumn;
        this.schemaKeyColumn  = schemaKeyColumn;
    }

    /**
     * Loads all alert type configurations from the DB config table.
     *
     * The schemaKey from each row is resolved to a classpath schema path using the
     * provided schemaMap. Fails fast if a schemaKey from the DB is not found in the map.
     *
     * @param schemaMap map of schemaKey → classpath schema path (from application.yml)
     * @return map of alertTypeId → AlertTypeProperties (viewName + resolved schemaPath)
     * @throws IllegalStateException if the config table is empty, or a schemaKey has no
     *                               matching entry in schemaMap
     */
    public Map<String, AlertTypeProperties> loadAlertTypeConfigs(Map<String, String> schemaMap) {
        String sql = String.format(
                "SELECT %s, %s, %s FROM %s",
                alertTypeColumn, viewNameColumn, schemaKeyColumn, viewConfigTable
        );

        log.debug("Loading alert type configs from table [{}]", viewConfigTable);

        Map<String, AlertTypeProperties> configs = new LinkedHashMap<>();

        jdbcTemplate.query(sql, rs -> {
            while (rs.next()) {
                String alertType  = rs.getString(1);
                String viewName   = rs.getString(2);
                String schemaKey  = rs.getString(3);

                if (alertType == null || viewName == null || schemaKey == null) {
                    log.warn("Skipping row with null value — alertType=[{}], viewName=[{}], schemaKey=[{}]",
                            alertType, viewName, schemaKey);
                    continue;
                }

                String schemaPath = schemaMap.get(schemaKey.trim());
                if (schemaPath == null) {
                    throw new IllegalStateException(
                        "[alert-util] DB config table [" + viewConfigTable + "] contains schemaKey ["
                        + schemaKey + "] for alertType [" + alertType + "], but no matching entry "
                        + "exists in alert-util.schema-map.\n"
                        + "Add the following to your application.yml:\n\n"
                        + "  alert-util:\n"
                        + "    schema-map:\n"
                        + "      " + schemaKey + ": schema/" + schemaKey + "_schema.json\n"
                    );
                }

                AlertTypeProperties props = new AlertTypeProperties();
                props.setViewName(viewName.trim());
                props.setSchemaPath(schemaPath);
                configs.put(alertType.trim(), props);
            }
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
