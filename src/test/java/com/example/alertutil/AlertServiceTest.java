package com.example.alertutil;

import com.example.alertutil.config.AlertUtilProperties.AlertTypeProperties;
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

import java.util.Map;
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

    private static final String DB_NAME      = "testDb";
    private static final String ALERT_ID     = "ALERT-001";
    private static final String ALERT_TYPE   = "10000";
    private static final String VIEW_NAME    = "v_alert_10000";
    private static final String SCHEMA_PATH  = "schema/10000_schema.json";
    private static final String VALID_JSON   = """
            {
              "alertId": "ALERT-001",
              "title": "Disk usage critical",
              "severity": "HIGH"
            }
            """;

    @BeforeEach
    void setUp() {
        Map<String, AlertTypeProperties> alertTypes = Map.of(
                ALERT_TYPE, alertTypeProps(VIEW_NAME, SCHEMA_PATH),
                "20000",    alertTypeProps("v_alert_20000", "schema/20000_schema.json")
        );
        alertService = spy(new AlertService(alertRepository, jsonSchemaValidator, objectMapper, alertTypes));
        doReturn(mockJdbcTemplate).when(alertService).resolveJdbcTemplate(anyString());
    }

    @Test
    void processAlert_success_returnsAlertResult() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(VALID_JSON);
        doNothing().when(jsonSchemaValidator).validate(anyString(), anyString(), any());

        AlertResult result = alertService.processAlert(DB_NAME, ALERT_ID, ALERT_TYPE);

        assertThat(result).isNotNull();
        assertThat(result.getAlertId()).isEqualTo(ALERT_ID);
        assertThat(result.getJson().get("severity").asText()).isEqualTo("HIGH");

        verify(alertRepository).fetchByAlertId(mockJdbcTemplate, VIEW_NAME, ALERT_ID);
        verify(jsonSchemaValidator).validate(eq(ALERT_ID), eq(SCHEMA_PATH), any());
        verify(alertService).resolveJdbcTemplate(DB_NAME);
    }

    @Test
    void processAlert_unknownAlertType_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> alertService.processAlert(DB_NAME, ALERT_ID, "99999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99999");

        verifyNoInteractions(alertRepository);
        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_alertNotFound_throwsAlertNotFoundException() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenThrow(new AlertNotFoundException(ALERT_ID));

        assertThatThrownBy(() -> alertService.processAlert(DB_NAME, ALERT_ID, ALERT_TYPE))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining(ALERT_ID);

        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_malformedJson_throwsAlertProcessingException() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn("NOT_VALID_JSON{{");

        assertThatThrownBy(() -> alertService.processAlert(DB_NAME, ALERT_ID, ALERT_TYPE))
                .isInstanceOf(AlertProcessingException.class)
                .hasMessageContaining("invalid JSON");

        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_validationFails_throwsAlertValidationException() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn("{\"alertId\": \"ALERT-001\"}");
        doThrow(new AlertValidationException(ALERT_ID, Set.of("title is required")))
                .when(jsonSchemaValidator).validate(eq(ALERT_ID), eq(SCHEMA_PATH), any());

        assertThatThrownBy(() -> alertService.processAlert(DB_NAME, ALERT_ID, ALERT_TYPE))
                .isInstanceOf(AlertValidationException.class)
                .hasMessageContaining("title is required");
    }

    @Test
    void processAlert_sameDbCalledTwice_cachesJdbcTemplate() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(VALID_JSON);
        doNothing().when(jsonSchemaValidator).validate(anyString(), anyString(), any());

        alertService.processAlert(DB_NAME, ALERT_ID, ALERT_TYPE);
        alertService.processAlert(DB_NAME, "ALERT-002", ALERT_TYPE);

        verify(alertService, times(2)).resolveJdbcTemplate(DB_NAME);
    }

    @Test
    void processAlert_differentAlertTypes_useCorrectViewAndSchema() {
        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(VALID_JSON);
        doNothing().when(jsonSchemaValidator).validate(anyString(), anyString(), any());

        alertService.processAlert(DB_NAME, ALERT_ID, "10000");
        alertService.processAlert(DB_NAME, "ALERT-002", "20000");

        verify(alertRepository).fetchByAlertId(mockJdbcTemplate, "v_alert_10000", ALERT_ID);
        verify(alertRepository).fetchByAlertId(mockJdbcTemplate, "v_alert_20000", "ALERT-002");
        verify(jsonSchemaValidator).validate(eq(ALERT_ID), eq("schema/10000_schema.json"), any());
        verify(jsonSchemaValidator).validate(eq("ALERT-002"), eq("schema/20000_schema.json"), any());
    }

    @Test
    void processAlert_differentDbNames_resolvesSeparately() {
        JdbcTemplate anotherJdbcTemplate = mock(JdbcTemplate.class);
        doReturn(mockJdbcTemplate).when(alertService).resolveJdbcTemplate("db1");
        doReturn(anotherJdbcTemplate).when(alertService).resolveJdbcTemplate("db2");

        when(alertRepository.fetchByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(VALID_JSON);
        doNothing().when(jsonSchemaValidator).validate(anyString(), anyString(), any());

        alertService.processAlert("db1", ALERT_ID, ALERT_TYPE);
        alertService.processAlert("db2", "ALERT-002", ALERT_TYPE);

        verify(alertRepository).fetchByAlertId(eq(mockJdbcTemplate), eq(VIEW_NAME), eq(ALERT_ID));
        verify(alertRepository).fetchByAlertId(eq(anotherJdbcTemplate), eq(VIEW_NAME), eq("ALERT-002"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static AlertTypeProperties alertTypeProps(String viewName, String schemaPath) {
        AlertTypeProperties props = new AlertTypeProperties();
        props.setViewName(viewName);
        props.setSchemaPath(schemaPath);
        return props;
    }
}
