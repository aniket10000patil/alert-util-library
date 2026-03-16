package com.barclays.alertutil;

import com.barclays.alertutil.exception.AlertNotFoundException;
import com.barclays.alertutil.exception.AlertProcessingException;
import com.barclays.alertutil.exception.AlertValidationException;
import com.barclays.alertutil.model.AlertResult;
import com.barclays.alertutil.repository.AlertRepository;
import com.barclays.alertutil.service.AlertService;
import com.barclays.alertutil.validator.JsonSchemaValidator;
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

    private static final String DB_NAME = "testDb";