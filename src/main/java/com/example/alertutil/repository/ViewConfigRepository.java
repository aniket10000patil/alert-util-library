package com.example.alertutil.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads view name mappings from a DB configuration table at startup.
 *
 * The configuration table maps a key (e.g. alert type) to a view name:
 *
 *   CREATE TABLE alert_view_config (
 *       alert_type VARCHAR(50) NOT NULL,
 *       view_name  VARCHAR(200) NOT NULL
 *   );
 *
 * SQL executed:
 *   SELECT {viewKeyColumn}, {viewNameColumn} FROM {viewConfigTable}
 */
public class ViewConfigRepository {

    private static final Logger log = LoggerFactory.getLogger(ViewConfigRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final String viewConfigTable;
    private final String viewKeyColumn;
    private final String viewNameColumn;

    public ViewConfigRepository(JdbcTemplate jdbcTemplate,
                                String viewConfigTable,
                                String viewKeyColumn,
                                String viewNameColumn) {
        this.jdbcTemplate    = jdbcTemplate;
        this.viewConfigTable = viewConfigTable;
        this.viewKeyColumn   = viewKeyColumn;
        this.viewNameColumn  = viewNameColumn;
    }

    /**
     * Loads all key → view name mappings from the configuration table.
     *
     * Called once at startup by {@code AlertUtilAutoConfiguration}.
     *
     * @return map of viewKey → viewName (e.g. "10000" → "v_alert_json_credit")
     * @throws IllegalStateException if the config table is empty or cannot be read
     */
    public Map<String, String> loadViewConfig() {
        String sql = String.format(
                "SELECT %s, %s FROM %s",
                viewKeyColumn, viewNameColumn, viewConfigTable
        );

        log.debug("Loading view config from table [{}]", viewConfigTable);

        Map<String, String> config = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            while (rs.next()) {
                String key      = rs.getString(1);
                String viewName = rs.getString(2);
                if (key != null && viewName != null) {
                    config.put(key.trim(), viewName.trim());
                }
            }
        });

        if (config.isEmpty()) {
            throw new IllegalStateException(
                "[alert-util] View config table [" + viewConfigTable + "] returned no rows. "
                + "Ensure the table exists and contains at least one key→view_name mapping."
            );
        }

        log.info("alert-util: Loaded {} view mapping(s) from [{}]: {}",
                config.size(), viewConfigTable, config);
        return config;
    }
}
