package com.example.alertutil.repository;

import com.example.alertutil.config.AlertUtilProperties;
import com.example.alertutil.exception.AlertNotFoundException;
import com.example.alertutil.exception.AlertProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.Reader;
import java.sql.Clob;

/**
 * Queries the configured DB view to fetch alert JSON by alertId.
 *
 * The view is responsible for HTML → JSON conversion.
 * This class simply reads whatever the view returns.
 *
 * Handles both VARCHAR/TEXT and CLOB column types transparently.
 *
 * SQL executed:
 *   SELECT {jsonColumn} FROM {viewName} WHERE {alertIdColumn} = ?
 */
public class AlertRepository {

    private static final Logger log = LoggerFactory.getLogger(AlertRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final AlertUtilProperties properties;

    public AlertRepository(JdbcTemplate jdbcTemplate, AlertUtilProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties   = properties;
    }

    /**
     * Fetches the JSON string from the given DB view for the given alertId.
     *
     * Supports:
     *  - VARCHAR / TEXT  → read directly as String
     *  - CLOB            → streamed via Reader to avoid truncation on large payloads
     *
     * @param alertId  the alert identifier
     * @param viewName the DB view to query
     * @return full JSON string from the view
     * @throws AlertNotFoundException   if no row found for alertId
     * @throws AlertProcessingException if CLOB cannot be read
     */
    public String fetchJsonByAlertId(String alertId, String viewName) {
        String sql = String.format(
                "SELECT %s FROM %s WHERE %s = ?",
                properties.getJsonColumn(),
                viewName,
                properties.getAlertIdColumn()
        );

        log.debug("Querying view [{}] for alertId [{}]", viewName, alertId);

        // ResultSetExtractor gives us direct control over the ResultSet.
        // We inspect the actual Java type returned by the JDBC driver
        // so we can handle both VARCHAR (String) and CLOB transparently.
        String result = jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null;  // no row found — handled below
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

        log.debug("Fetched JSON for alertId [{}] ({} chars)", alertId, result.length());
        return result;
    }

    /**
     * Streams full CLOB content into a String via its Reader.
     *
     * Uses a 4KB buffer for efficient reading.
     * Avoids truncation that can occur with clob.getSubString() on large payloads.
     *
     * @param alertId used for error reporting only
     * @param clob    the CLOB object returned by the JDBC driver
     * @return full CLOB content as a String
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
