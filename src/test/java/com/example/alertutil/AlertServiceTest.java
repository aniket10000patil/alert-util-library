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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private JsonSchemaValidator jsonSchemaValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(alertRepository, jsonSchemaValidator, objectMapper);
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void processAlert_happyPath_returnsValidatedResult() {
        String alertId = "ALERT-001";
        String jsonFromView = """
                {
                  "alertId":   "ALERT-001",
                  "alertType": "10000",
                  "title":     "Server Down",
                  "severity":  "HIGH"
                }
                """;

        when(alertRepository.fetchJsonByAlertId(alertId)).thenReturn(jsonFromView);
        doNothing().when(jsonSchemaValidator).validate(eq(alertId), any(JsonNode.class));

        AlertResult result = alertService.processAlert(alertId);

        assertThat(result).isNotNull();
        assertThat(result.getAlertId()).isEqualTo(alertId);
        assertThat(result.getJson().get("severity").asText()).isEqualTo("HIGH");

        verify(alertRepository).fetchJsonByAlertId(alertId);
        verify(jsonSchemaValidator).validate(eq(alertId), any(JsonNode.class));
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    void processAlert_alertNotFound_throwsAlertNotFoundException() {
        String alertId = "MISSING-999";
        when(alertRepository.fetchJsonByAlertId(alertId))
                .thenThrow(new AlertNotFoundException(alertId));

        assertThatThrownBy(() -> alertService.processAlert(alertId))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining("MISSING-999");

        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_malformedJson_throwsAlertProcessingException() {
        String alertId = "ALERT-002";
        when(alertRepository.fetchJsonByAlertId(alertId)).thenReturn("NOT_VALID_JSON{{{{");

        assertThatThrownBy(() -> alertService.processAlert(alertId))
                .isInstanceOf(AlertProcessingException.class)
                .hasMessageContaining("invalid JSON");

        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_validationFails_throwsAlertValidationException() {
        String alertId = "ALERT-003";
        String jsonFromView = "{\"alertId\":\"ALERT-003\",\"alertType\":\"10000\"}";

        when(alertRepository.fetchJsonByAlertId(alertId)).thenReturn(jsonFromView);
        doThrow(new AlertValidationException(alertId, Set.of("$.title: is missing")))
                .when(jsonSchemaValidator).validate(eq(alertId), any(JsonNode.class));

        assertThatThrownBy(() -> alertService.processAlert(alertId))
                .isInstanceOf(AlertValidationException.class)
                .hasMessageContaining("$.title: is missing");
    }

    // -------------------------------------------------------------------------
    // Multiple alerts processed independently
    // -------------------------------------------------------------------------

    @Test
    void processAlert_multipleAlerts_eachProcessedIndependently() {
        String json1 = "{\"alertId\":\"A-1\",\"alertType\":\"10000\",\"title\":\"Alert 1\"}";
        String json2 = "{\"alertId\":\"A-2\",\"alertType\":\"20000\",\"title\":\"Alert 2\"}";

        when(alertRepository.fetchJsonByAlertId("A-1")).thenReturn(json1);
        when(alertRepository.fetchJsonByAlertId("A-2")).thenReturn(json2);
        doNothing().when(jsonSchemaValidator).validate(any(), any(JsonNode.class));

        AlertResult result1 = alertService.processAlert("A-1");
        AlertResult result2 = alertService.processAlert("A-2");

        assertThat(result1.getJson().get("title").asText()).isEqualTo("Alert 1");
        assertThat(result2.getJson().get("title").asText()).isEqualTo("Alert 2");

        verify(alertRepository).fetchJsonByAlertId("A-1");
        verify(alertRepository).fetchJsonByAlertId("A-2");
    }
}
