package com.example.alertutil.repository;

import com.example.alertutil.config.AlertUtilProperties;
import com.example.alertutil.exception.AlertNotFoundException;
import com.example.alertutil.exception.AlertProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.Reader;
import java.sql.Clob;

/**
 * Queries the application-specific DB view to retrieve a JSON string for the given alertId.
 *
 * Handles both VARCHAR/TEXT and CLOB column types transparently.
 * The view is responsible for the HTML → JSON conversion.
 *
 * SQL executed:
 *   SELECT <json-column> FROM <view-name> WHERE <alert-id-column> = ?
 */
@Repository
public class AlertRepository {

    private static final Logger log = LoggerFactory.getLogger(AlertRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final AlertUtilProperties properties;

    public AlertRepository(JdbcTemplate jdbcTemplate, AlertUtilProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /**
     * Fetches the JSON string from the configured DB view for the given alertId.
     *
     * Handles column types:
     *  - VARCHAR / TEXT  → read directly as String
     *  - CLOB            → streamed via Reader to avoid truncation
     *
     * @param alertId the alert identifier
     * @return full JSON string from the view's json column
     * @throws AlertNotFoundException   if no row is found for the given alertId
     * @throws AlertProcessingException if the CLOB content cannot be read
     */
    public String fetchJsonByAlertId(String alertId) {
        String sql = String.format(
                "SELECT %s FROM %s WHERE %s = ?",
                properties.getJsonColumn(),
                properties.getViewName(),
                properties.getAlertIdColumn()
        );

        log.debug("Executing view query: [{}] with alertId: [{}]", sql, alertId);

        // ResultSetExtractor gives direct control over ResultSet —
        // we inspect the actual Java type returned by the JDBC driver
        // so we can handle both VARCHAR (String) and CLOB transparently.
        String result = jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return null; // no row found
            }

            Object value = rs.getObject(1);

            if (value == null) {
                return null;
            }

            // CLOB — stream the full content to avoid driver-level truncation
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
     * Avoids truncation that can occur with clob.getSubString() on large payloads.
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