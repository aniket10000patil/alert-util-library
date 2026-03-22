package com.example.alertutil;

import com.example.alertutil.exception.AlertNotFoundException;
import com.example.alertutil.exception.AlertProcessingException;
import com.example.alertutil.exception.AlertValidationException;
import com.example.alertutil.model.AlertResult;
import com.example.alertutil.repository.AlertRepository;
import com.example.alertutil.service.AlertService;
import com.example.alertutil.validator.JsonSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private JsonSchemaValidator jsonSchemaValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AlertService alertService;

    @Mock
    private JdbcTemplate mockJdbcTemplate;

    private static final String DB_NAME    = "testDb";
    private static final String SCHEMA     = "testSchema";
    private static final String ALERT_ID   = "ALERT-001";
    private static final String ALERT_TYPE = "10000";
    private static final String VALID_JSON = """
            {
              "alertId": "ALERT-001",
              "title": "Disk usage critical",
              "severity": "HIGH"
            }
            """;

    @BeforeEach
    void setUp() {
        alertService = spy(new AlertService(alertRepository, jsonSchemaValidator, objectMapper));
        doReturn(mockJdbcTemplate).when(alertService).resolveJdbcTemplate(anyString());
    }

    @Test
    void processAlert_success_returnsAlertResult() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(VALID_JSON);
        doNothing().when(jsonSchemaValidator).validate(anyString(), anyString(), any());

        AlertResult result = alertService.processAlert(DB_NAME, SCHEMA, ALERT_ID, ALERT_TYPE);

        assertThat(result).isNotNull();
        assertThat(result.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(result.getJson().get("severity").asText()).isEqualTo("HIGH");

        verify(alertRepository).fetchByAlertId(mockJdbcTemplate, SCHEMA, ALERT_ID);
        verify(jsonSchemaValidator).validate(eq(ALERT_ID), eq(ALERT_TYPE), any());
        verify(alertService).resolveJdbcTemplate(DB_NAME);
    }

    @Test
    void processAlert_alertNotFound_throwsAlertNotFoundException() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenThrow(new AlertNotFoundException(ALERT_ID));

        assertThatThrownBy(() -> alertService.processAlert(DB_NAME, SCHEMA, ALERT_ID, ALERT_TYPE))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining(ALERT_ID);

        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_malformedJson_throwsAlertProcessingException() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn("NOT_VALID_JSON{{");

        assertThatThrownBy(() -> alertService.processAlert(DB_NAME, SCHEMA, ALERT_ID, ALERT_TYPE))
                .isInstanceOf(AlertProcessingException.class)
                .hasMessageContaining("invalid JSON");

        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_validationFails_throwsAlertValidationException() {
        String minimalJson = "{\"alertId\": \"ALERT-001\"}";

        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(minimalJson);
        doThrow(new AlertValidationException(ALERT_ID, Set.of("title is required")))
                .when(jsonSchemaValidator).validate(eq(ALERT_ID), eq(ALERT_TYPE), any());

        assertThatThrownBy(() -> alertService.processAlert(DB_NAME, SCHEMA, ALERT_ID, ALERT_TYPE))
                .isInstanceOf(AlertValidationException.class)
                .hasMessageContaining("title is required");
    }

    @Test
    void processAlert_sameDbCalledTwice_cachesJdbcTemplate() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(VALID_JSON);
        doNothing().when(jsonSchemaValidator).validate(anyString(), anyString(), any());

        alertService.processAlert(DB_NAME, SCHEMA, ALERT_ID, ALERT_TYPE);
        alertService.processAlert(DB_NAME, SCHEMA, "ALERT-002", ALERT_TYPE);

        verify(alertService, times(2)).resolveJdbcTemplate(DB_NAME);
        verify(alertRepository, times(2)).fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString());
    }

    @Test
    void processAlert_nullJsonFromView_throwsException() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(null);

        assertThatThrownBy(() -> alertService.processAlert(DB_NAME, SCHEMA, ALERT_ID, ALERT_TYPE))
                .isInstanceOf(Exception.class);

        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_differentDbNames_resolvesSeparately() {
        JdbcTemplate anotherJdbcTemplate = mock(JdbcTemplate.class);
        doReturn(mockJdbcTemplate).when(alertService).resolveJdbcTemplate("db1");
        doReturn(anotherJdbcTemplate).when(alertService).resolveJdbcTemplate("db2");

        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(VALID_JSON);
        doNothing().when(jsonSchemaValidator).validate(anyString(), anyString(), any());

        alertService.processAlert("db1", SCHEMA, ALERT_ID, ALERT_TYPE);
        alertService.processAlert("db2", SCHEMA, "ALERT-002", ALERT_TYPE);

        verify(alertRepository).fetchByAlertId(eq(mockJdbcTemplate), eq(SCHEMA), eq(ALERT_ID));
        verify(alertRepository).fetchByAlertId(eq(anotherJdbcTemplate), eq(SCHEMA), eq("ALERT-002"));
    }

    @Test
    void processAlert_differentAlertTypes_useDifferentSchemas() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(VALID_JSON);
        doNothing().when(jsonSchemaValidator).validate(anyString(), anyString(), any());

        alertService.processAlert(DB_NAME, SCHEMA, ALERT_ID, "10000");
        alertService.processAlert(DB_NAME, SCHEMA, "ALERT-002", "20000");

        verify(jsonSchemaValidator).validate(eq(ALERT_ID), eq("10000"), any());
        verify(jsonSchemaValidator).validate(eq("ALERT-002"), eq("20000"), any());
    }
}
