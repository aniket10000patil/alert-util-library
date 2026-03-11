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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private static final String DEFAULT_VIEW = "v_alert_json_default";
    private static final String VIEW_10000   = "v_alert_json_credit";
    private static final String VIEW_20000   = "v_alert_json_equity";

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private JsonSchemaValidator jsonSchemaValidator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Single-view (default view-name) — existing behaviour
    // -------------------------------------------------------------------------

    @Test
    void processAlert_happyPath_returnsValidatedResult() throws Exception {
        AlertService alertService = serviceWithDefaultView(DEFAULT_VIEW);

        String alertId = "ALERT-001";
        String jsonFromView = """
                {
                  "alertId":   "ALERT-001",
                  "alertType": "10000",
                  "title":     "Server Down",
                  "severity":  "HIGH"
                }
                """;

        when(alertRepository.fetchJsonByAlertId(alertId, DEFAULT_VIEW)).thenReturn(jsonFromView);
        doNothing().when(jsonSchemaValidator).validate(eq(alertId), any(JsonNode.class));

        AlertResult result = alertService.processAlert(alertId);

        assertThat(result).isNotNull();
        assertThat(result.getAlertId()).isEqualTo(alertId);
        assertThat(result.getJson().get("severity").asText()).isEqualTo("HIGH");

        verify(alertRepository).fetchJsonByAlertId(alertId, DEFAULT_VIEW);
        verify(jsonSchemaValidator).validate(eq(alertId), any(JsonNode.class));
    }

    @Test
    void processAlert_alertNotFound_throwsAlertNotFoundException() {
        AlertService alertService = serviceWithDefaultView(DEFAULT_VIEW);

        String alertId = "MISSING-999";
        when(alertRepository.fetchJsonByAlertId(alertId, DEFAULT_VIEW))
                .thenThrow(new AlertNotFoundException(alertId));

        assertThatThrownBy(() -> alertService.processAlert(alertId))
                .isInstanceOf(AlertNotFoundException.class)
                .hasMessageContaining("MISSING-999");

        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_malformedJson_throwsAlertProcessingException() {
        AlertService alertService = serviceWithDefaultView(DEFAULT_VIEW);

        String alertId = "ALERT-002";
        when(alertRepository.fetchJsonByAlertId(alertId, DEFAULT_VIEW)).thenReturn("NOT_VALID_JSON{{{{");

        assertThatThrownBy(() -> alertService.processAlert(alertId))
                .isInstanceOf(AlertProcessingException.class)
                .hasMessageContaining("invalid JSON");

        verifyNoInteractions(jsonSchemaValidator);
    }

    @Test
    void processAlert_validationFails_throwsAlertValidationException() {
        AlertService alertService = serviceWithDefaultView(DEFAULT_VIEW);

        String alertId = "ALERT-003";
        String jsonFromView = "{\"alertId\":\"ALERT-003\",\"alertType\":\"10000\"}";

        when(alertRepository.fetchJsonByAlertId(alertId, DEFAULT_VIEW)).thenReturn(jsonFromView);
        doThrow(new AlertValidationException(alertId, Set.of("$.title: is missing")))
                .when(jsonSchemaValidator).validate(eq(alertId), any(JsonNode.class));

        assertThatThrownBy(() -> alertService.processAlert(alertId))
                .isInstanceOf(AlertValidationException.class)
                .hasMessageContaining("$.title: is missing");
    }

    @Test
    void processAlert_noDefaultView_throwsIllegalStateException() {
        AlertService alertService = serviceWithDefaultView(null);

        assertThatThrownBy(() -> alertService.processAlert("ALERT-001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("view-name");
    }

    // -------------------------------------------------------------------------
    // Multi-view — DB config table driven
    // -------------------------------------------------------------------------

    @Test
    void processAlert_withViewKey_selectsCorrectViewFromDbConfig() throws Exception {
        Map<String, String> viewConfig = Map.of("10000", VIEW_10000, "20000", VIEW_20000);
        AlertService alertService = serviceWithViewConfig(viewConfig);

        String alertId = "ALERT-100";
        String jsonFromView = "{\"alertId\":\"ALERT-100\",\"alertType\":\"10000\",\"title\":\"Limit Breach\"}";

        when(alertRepository.fetchJsonByAlertId(alertId, VIEW_10000)).thenReturn(jsonFromView);
        doNothing().when(jsonSchemaValidator).validate(eq(alertId), any(JsonNode.class));

        AlertResult result = alertService.processAlert(alertId, "10000");

        assertThat(result.getAlertId()).isEqualTo(alertId);
        assertThat(result.getJson().get("title").asText()).isEqualTo("Limit Breach");
        verify(alertRepository).fetchJsonByAlertId(alertId, VIEW_10000);
    }

    @Test
    void processAlert_withViewKey_differentTypeUsesCorrectView() throws Exception {
        Map<String, String> viewConfig = Map.of("10000", VIEW_10000, "20000", VIEW_20000);
        AlertService alertService = serviceWithViewConfig(viewConfig);

        String alertId = "ALERT-200";
        String jsonFromView = "{\"alertId\":\"ALERT-200\",\"alertType\":\"20000\",\"title\":\"Price Move\"}";

        when(alertRepository.fetchJsonByAlertId(alertId, VIEW_20000)).thenReturn(jsonFromView);
        doNothing().when(jsonSchemaValidator).validate(eq(alertId), any(JsonNode.class));

        AlertResult result = alertService.processAlert(alertId, "20000");

        assertThat(result.getAlertId()).isEqualTo(alertId);
        verify(alertRepository).fetchJsonByAlertId(alertId, VIEW_20000);
    }

    @Test
    void processAlert_unknownViewKey_throwsIllegalArgumentException() {
        Map<String, String> viewConfig = Map.of("10000", VIEW_10000);
        AlertService alertService = serviceWithViewConfig(viewConfig);

        assertThatThrownBy(() -> alertService.processAlert("ALERT-001", "99999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99999")
                .hasMessageContaining("10000");
    }

    @Test
    void processAlert_withViewKey_butNoConfigTable_throwsIllegalStateException() {
        AlertService alertService = serviceWithDefaultView(DEFAULT_VIEW);  // no viewConfigMap

        assertThatThrownBy(() -> alertService.processAlert("ALERT-001", "10000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("view-config-table");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AlertService serviceWithDefaultView(String viewName) {
        return new AlertService(alertRepository, jsonSchemaValidator, objectMapper,
                viewName, null);
    }

    private AlertService serviceWithViewConfig(Map<String, String> viewConfig) {
        return new AlertService(alertRepository, jsonSchemaValidator, objectMapper,
                null, viewConfig);
    }
}
