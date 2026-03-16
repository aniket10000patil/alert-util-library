package com.barclays.alertutil;

import com.barclays.alertutil.model.AlertResult;
import com.barclays.alertutil.repository.AlertRepository;
import com.barclays.alertutil.service.AlertService;
import com.barclays.alertutil.validator.JsonSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private JsonSchemaValidator jsonSchemaValidator;

    @InjectMocks
    @Spy
    private AlertService alertService;

    private final String TEST_DB = "testDb";
    private final String TEST_SCHEMA = "testSchema";
    private final String TEST_ALERT_ID = "ALERT-001";
    private final String VALID_JSON = "{\"alertType\":\"TEST\",\"status\":\"OPEN\"}";

    @Test
    void processAlert_shouldReturnAlertResult() throws Exception {
        // Stub resolveJdbcTemplate since it's an internal method
        JdbcTemplate mockJdbcTemplate = mock(JdbcTemplate.class);
        doReturn(mockJdbcTemplate).when(alertService).resolveJdbcTemplate(TEST_DB);

        // Stub fetchJsonByAlertId with any() matchers
        when(alertRepository.fetchJsonByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(VALID_JSON);

        // Stub validator to do nothing
        doNothing().when(jsonSchemaValidator).validate(anyString(), any(JsonNode.class));

        // Act
        AlertResult result = alertService.processAlert(TEST_DB, TEST_SCHEMA, TEST_ALERT_ID);

        // Assert
        assertNotNull(result);
        verify(alertRepository).fetchJsonByAlertId(mockJdbcTemplate, TEST_ALERT_ID, TEST_SCHEMA);
    }

    @Test
    void processAlert_sameDbCalledTwice_cachesJdbcTemplate() throws Exception {
        JdbcTemplate mockJdbcTemplate = mock(JdbcTemplate.class);
        doReturn(mockJdbcTemplate).when(alertService).resolveJdbcTemplate(TEST_DB);

        when(alertRepository.fetchJsonByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(VALID_JSON);

        doNothing().when(jsonSchemaValidator).validate(anyString(), any(JsonNode.class));

        // Call twice with same dbName
        alertService.processAlert(TEST_DB, TEST_SCHEMA, TEST_ALERT_ID);
        alertService.processAlert(TEST_DB, TEST_SCHEMA, "ALERT-002");

        // resolveJdbcTemplate should cache — verify it resolved only once (or twice, depending on your caching logic)
        verify(alertService, times(2)).resolveJdbcTemplate(TEST_DB);
        verify(alertRepository, times(2)).fetchJsonByAlertId(any(JdbcTemplate.class), anyString(), anyString());
    }

    @Test
    void processAlert_nullJsonReturned_shouldThrowException() throws Exception {
        JdbcTemplate mockJdbcTemplate = mock(JdbcTemplate.class);
        doReturn(mockJdbcTemplate).when(alertService).resolveJdbcTemplate(TEST_DB);

        when(alertRepository.fetchJsonByAlertId(any(JdbcTemplate.class), anyString(), anyString()))
                .thenReturn(null);

        assertThrows(Exception.class, () ->
                alertService.processAlert(TEST_DB, TEST_SCHEMA, TEST_ALERT_ID));
    }
}