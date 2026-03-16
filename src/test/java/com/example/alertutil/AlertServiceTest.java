package com.example.alertutil;

import com.example.alertutil.exception.AlertNotFoundException;
import com.example.alertutil.exception.AlertProcessingException;
import com.example.alertutil.exception.AlertValidationException;
import com.example.alertutil.model.AlertResult;
import com.example.alertutil.repository.AlertRepository;
import com.example.alertutil.service.AlertService;
import com.example.alertutil.validator.JsonSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final String PRIMARY_DB   = "primaryDb";
    private static final String SECONDARY_DB = "secondaryDb";
    private static final String SCHEMA_PRIMARY   = "CREDIT_SCHEMA";
    private static final String SCHEMA_SECONDARY = "EQUITY_SCHEMA";

    @Mock private AlertRepository alertRepository;
    @Mock private JsonSchemaValidator jsonSchemaValidator;
    @Mock private ApplicationContext applicationContext;
    @Mock private DataSource primaryDataSource;
    @Mock private DataSource secondaryDataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(
                alertRepository, jsonSchemaValidator, objectMapper, applicationContext);
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void processAlert_happyPath_returnsValidatedResult() {
        stubDataSource(PRIMARY_DB, primaryDataSource);

        String alertId = "ALERT-001";
        String jsonFromView = """
                {
                  "alertId":   "ALERT-001",
                  "title":     "Server Down",
                  "severity":  "HIGH"
                }
                """;

        when(alertRepository.fetchJsonByAlertId(any(JdbcTemplate.class), eq(SCHEMA_PRIMARY), eq(alertId)))
                .thenReturn(jsonFromView);
        doNothing().when(jsonSchemaValidator).validate(eq(alertId), any(JsonNode.class));

        AlertResult result = alertService.processAlert(PRIMARY_DB, SCHEMA_PRIMARY, alertId);

        assertThat(result).isNotNull();
        assertThat(result.getAlertId()).isEqualTo(alertId);
        assertThat(result.getJson().get("severity").asText()).isEqualTo("HIGH");

        verify(alertRepository).fetchJsonByAlertId(any(JdbcTemplate.class), eq(SCHEMA_PRIMARY), eq(alertId));
        verify(jsonSchemaValidator).validate(eq(alertId), any(JsonNode.class));
    }

    // -------------------------------------------------------------------------
    // Multiple databases with different schemas
    // -------------------------------------------------------------------------

    @Test
    void processAlert_differentDbAndSchema_eachResolvesCorrectly() {
        stubDataSource(PRIMARY_DB, primaryDataSource);
        stubDataSource(SECONDARY_DB, secondaryDataSource);

        String json1 = "{\"alertId\":\"A-1\",\"title\":\"Primary Alert\"}";
        String json2 = "{\"alertId\":\"A-2\",\"title\":\"Secondary Alert\"}";

        when(alertRepository.fetchJsonByAlertId(any(JdbcTemplate.class), eq(SCHEMA_PRIMARY), eq("A-1")))
                .thenReturn(json1);
        when(alertRepository.fetchJsonByAlertId(any(JdbcTemplate.class), eq(SCHEMA_SECONDARY), eq("A-2")))
                .thenReturn(json2);
        doNothing().when(jsonSchemaValidator).validate(any(), any(JsonNode.class));

        AlertResult result1 = alertService.processAlert(PRIMARY_DB, SCHEMA_PRIMARY, "A-1");
        AlertResult result2 = alertService.processAlert(SECONDARY_DB, SCHEMA_SECONDARY, "A-2");

        assertThat(result1.getJson().get("title").asText()).isEqualTo("Primary Alert");
        assertThat(result2.getJson().get("title").asText()).isEqualTo("Secondary Alert");

        // DataSource resolved once per dbName
        verify(applicationContext, times(1)).getBean(PRIMARY_DB, DataSource.class);
        verify(applicationContext, times(1)).getBean(SECONDARY_DB, DataSource.class);
    }

    @Test
    void processAlert_sameDbCalledTwice_cachesJdbcTemplate() {
        stubDataSource(PRIMARY_DB, primaryDataSource);

        String json = "{\"alertId\":\"A-1\",\"title\":\"Alert\"}";
        when(alertRepository.fetchJsonByAlertId(any(JdbcTemplate.class), eq(SCHEMA_PRIMARY), any()))
                .thenReturn(json);
        doNothing().when(jsonSchemaValidator).validate(any(), any(JsonNode.class));

        alertService.processAlert(PRIMARY_DB, SCHEMA_PRIMARY, "A-1");
        alertService.processAlert(PRIMARY_DB, SCHEMA_PRIMARY, "A-2");

        // DataSource resolved only once — JdbcTemplate is cached
        verify(applicationContext, times(1)).getBean(PRIMARY_DB, DataSource.class);
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    void processAlert_unknownDbName_throwsIllegalStateException() {
        when(applicationContext.getBean("unknownDb", DataSource.class))
                .thenThrow(new org.springframework.beans.factory.NoSuchBeanDefinitionException("unknownDb"));

        assertThatThrownBy(() -> alertService.processAlert("unknownDb", "SOME_SCHEMA", "A-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknownDb");
    }

    @Test
    void processAlert_alertNotFound_throwsAlertNotFoundException() {
        stubDataSource(PRIMARY_DB, primaryDataSource);

        when(alertRepository.fetchJsonByAlertId(any(JdbcTemplate.class), eq(SCHEMA_PRIMARY), eq("MISSING")))
                .thenThrow(new AlertNotFoundException("MISSING"));

        assertThatThrownBy(() -> alertService.processAlert(PRIMARY_DB, SCHEMA_PRIMARY, "MISSING"))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining("MISSING");

        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_malformedJson_throwsAlertProcessingException() {
        stubDataSource(PRIMARY_DB, primaryDataSource);

        when(alertRepository.fetchJsonByAlertId(any(JdbcTemplate.class), eq(SCHEMA_PRIMARY), eq("A-1")))
                .thenReturn("NOT_VALID_JSON{{{{");

        assertThatThrownBy(() -> alertService.processAlert(PRIMARY_DB, SCHEMA_PRIMARY, "A-1"))
                .isInstanceOf(AlertProcessingException.class)
                .hasMessageContaining("invalid JSON");

        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_validationFails_throwsAlertValidationException() {
        stubDataSource(PRIMARY_DB, primaryDataSource);

        String json = "{\"alertId\":\"A-1\"}";
        when(alertRepository.fetchJsonByAlertId(any(JdbcTemplate.class), eq(SCHEMA_PRIMARY), eq("A-1")))
                .thenReturn(json);
        doThrow(new AlertValidationException("A-1", Set.of("$.title: is missing")))
                .when(jsonSchemaValidator).validate(eq("A-1"), any(JsonNode.class));

        assertThatThrownBy(() -> alertService.processAlert(PRIMARY_DB, SCHEMA_PRIMARY, "A-1"))
                .isInstanceOf(AlertValidationException.class)
                .hasMessageContaining("$.title: is missing");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void stubDataSource(String dbName, DataSource dataSource) {
        when(applicationContext.getBean(dbName, DataSource.class)).thenReturn(dataSource);
    }
}
