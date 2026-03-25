package com.example.alertutil.repository;

import com.example.alertutil.exception.AlertNotFoundException;
import com.example.alertutil.exception.AlertProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.Reader;
import java.sql.Clob;

/**
 * Queries a DB view to fetch alert JSON by alertInternalId.
 *
 * The view name is resolved per call from the alert-type config — different alert types
 * query different views. Column names are shared across all alert types and configured once.
 *
 * Handles both VARCHAR/TEXT and CLOB column types transparently.
 *
 * SQL executed:
 *   SELECT {jsonColumn} FROM {viewName} WHERE {alertInternalIdColumn} = ?
 */
public class AlertRepository {

    private static final Logger log = LoggerFactory.getLogger(AlertRepository.class);

    private final String alertInternalIdColumn;
    private final String jsonColumn;

    public AlertRepository(String alertInternalIdColumn, String jsonColumn) {
        this.alertInternalIdColumn = alertInternalIdColumn;
        this.jsonColumn            = jsonColumn;
    }

    /**
     * Fetches the JSON string from the given view for the specified alertInternalId.
     *
     * @param jdbcTemplate    the JdbcTemplate for the target database
     * @param viewName        the DB view to query (resolved from alert-type config)
     * @param alertInternalId the alert internal identifier (Long for DB performance)
     * @return full JSON string from the view
     * @throws AlertNotFoundException   if no row found for alertInternalId
     * @throws AlertProcessingException if CLOB cannot be read
     */
    public String fetchByAlertInternalId(JdbcTemplate jdbcTemplate, String viewName, Long alertInternalId) {
        String sql = String.format(
                "SELECT %s FROM %s WHERE %s = ?",
                jsonColumn,
                viewName,
                alertInternalIdColumn
        );

        log.debug("Querying view [{}] for alertInternalId [{}]", viewName, alertInternalId);

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
                return readClob(alertInternalId, clob);
            }

            // VARCHAR, TEXT, NVARCHAR — driver already gave us a String
            return value.toString();

        }, alertInternalId);

        if (result == null) {
            throw new AlertNotFoundException(alertInternalId);
        }

        log.debug("Fetched JSON for alertInternalId [{}] from view [{}] ({} chars)", alertInternalId, viewName, result.length());
        return result;
    }

    /**
     * Streams full CLOB content into a String via its Reader.
     * Uses a 4KB buffer for efficient reading.
     */
    private String readClob(Long alertInternalId, Clob clob) {
        try (Reader reader = clob.getCharacterStream()) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int charsRead;
            while ((charsRead = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, charsRead);
            }
            log.debug("Read CLOB for alertInternalId [{}] ({} chars)", alertInternalId, sb.length());
            return sb.toString();
        } catch (Exception e) {
            throw new AlertProcessingException(
                    alertInternalId, "Failed to read CLOB content: " + e.getMessage(), e);
        }
    }
}
