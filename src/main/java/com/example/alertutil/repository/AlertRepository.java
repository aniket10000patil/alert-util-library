package com.example.alertutil.repository;

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
<<<<<<< HEAD
 * The view name and column names are fixed from config. The JdbcTemplate
 * (i.e. which database to hit) is passed per call by AlertService.
 *
=======
 * The view name and column names are read from {@link AlertUtilProperties}.
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
 * Handles both VARCHAR/TEXT and CLOB column types transparently.
 *
 * SQL executed:
 *   SELECT {jsonColumn} FROM {viewName} WHERE {alertIdColumn} = ?
 */
public class AlertRepository {

    private static final Logger log = LoggerFactory.getLogger(AlertRepository.class);

<<<<<<< HEAD
=======
    private final JdbcTemplate jdbcTemplate;
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
    private final String viewName;
    private final String alertIdColumn;
    private final String jsonColumn;

<<<<<<< HEAD
    public AlertRepository(String viewName, String alertIdColumn, String jsonColumn) {
        this.viewName      = viewName;
        this.alertIdColumn = alertIdColumn;
        this.jsonColumn    = jsonColumn;
=======
    public AlertRepository(JdbcTemplate jdbcTemplate, AlertUtilProperties properties) {
        this.jdbcTemplate  = jdbcTemplate;
        this.viewName      = properties.getViewName();
        this.alertIdColumn = properties.getAlertIdColumn();
        this.jsonColumn    = properties.getJsonColumn();
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
    }

    /**
     * Fetches the JSON string from the configured view for the given alertId,
     * using the provided JdbcTemplate (which is bound to a specific DataSource).
     *
<<<<<<< HEAD
     * @param jdbcTemplate the JdbcTemplate for the target database
     * @param alertId      the alert identifier
=======
     * Supports:
     *  - VARCHAR / TEXT  → read directly as String
     *  - CLOB            → streamed via Reader to avoid truncation on large payloads
     *
     * @param alertId  the alert identifier
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
     * @return full JSON string from the view
     * @throws AlertNotFoundException   if no row found for alertId
     * @throws AlertProcessingException if CLOB cannot be read
     */
    public String fetchJsonByAlertId(JdbcTemplate jdbcTemplate, String alertId) {
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

        log.debug("Fetched JSON for alertId [{}] ({} chars)", alertId, result.length());
        return result;
    }

    /**
     * Streams full CLOB content into a String via its Reader.
     * Uses a 4KB buffer for efficient reading.
<<<<<<< HEAD
=======
     * Avoids truncation that can occur with clob.getSubString() on large payloads.
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
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
