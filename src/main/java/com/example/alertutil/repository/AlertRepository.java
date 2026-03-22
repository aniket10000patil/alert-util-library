package com.example.alertutil.repository;

import com.example.alertutil.exception.AlertNotFoundException;
import com.example.alertutil.exception.AlertProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.Reader;
import java.sql.Clob;

/**
 * Queries a DB view to fetch alert JSON by alertId.
 *
 * The view name is resolved per call from the alert-type config — different alert types
 * query different views. Column names are shared across all alert types and configured once.
 *
 * Handles both VARCHAR/TEXT and CLOB column types transparently.
 *
 * SQL executed:
 *   SELECT {jsonColumn} FROM {viewName} WHERE {alertIdColumn} = ?
 */
public class AlertRepository {

    private static final Logger log = LoggerFactory.getLogger(AlertRepository.class);

    private final String alertIdColumn;
    private final String jsonColumn;

    public AlertRepository(String alertIdColumn, String jsonColumn) {
        this.alertIdColumn = alertIdColumn;
        this.jsonColumn    = jsonColumn;
    }

    /**
     * Fetches the JSON string from the given view for the specified alertId.
     *
     * @param jdbcTemplate the JdbcTemplate for the target database
     * @param viewName     the DB view to query (resolved from alert-type config)
     * @param alertId      the alert identifier
     * @return full JSON string from the view
     * @throws AlertNotFoundException   if no row found for alertId
     * @throws AlertProcessingException if CLOB cannot be read
     */
    public String fetchByAlertId(JdbcTemplate jdbcTemplate, String viewName, String alertId) {
        String sql = String.format(
                "SELECT %s FROM %s WHERE %s = ?",
                jsonColumn,
                viewName,
                alertIdColumn
        );

        log.debug("Querying view [{}] for alertId [{}]", viewName, alertId);

        String result = jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }

            Object value = rs.getObject(1);

            if (value == null) {
                return null;
            }

            // CLOB — stream the full content to avoid truncation
            if (value instanceof Clob clob) {
                return readClob(alertId, clob);
            }

            // VARCHAR, TEXT, NVARCHAR — driver already gave us a String
            return value.toString();

        }, alertId);

        if (result == null) {
            throw new AlertNotFoundException(alertId);
        }

        log.debug("Fetched JSON for alertId [{}] from view [{}] ({} chars)", alertId, viewName, result.length());
        return result;
    }

    /**
     * Streams full CLOB content into a String via its Reader.
     * Uses a 4KB buffer for efficient reading.
     */
    private String readClob(String alertId, Clob clob) {
        try (Reader reader = clob.getCharacterStream()) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int charsRead;
            while ((charsRead = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, charsRead);
            }
            log.debug("Read CLOB for alertId [{}] ({} chars)", alertId, sb.length());
            return sb.toString();
        } catch (Exception e) {
            throw new AlertProcessingException(
                    alertId, "Failed to read CLOB content: " + e.getMessage(), e);
        }
    }
}
