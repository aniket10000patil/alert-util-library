package com.example.alertutil;

import com.example.alertutil.config.AlertUtilProperties.AlertTypeProperties;
import com.example.alertutil.repository.ViewConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViewConfigRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ResultSet resultSet;

    private ViewConfigRepository repository;

    private static final Map<String, String> SCHEMA_MAP = Map.of(
            "credit", "schema/credit_schema.json",
            "equity", "schema/equity_schema.json"
    );

    @BeforeEach
    void setUp() {
        repository = new ViewConfigRepository(
                jdbcTemplate,
                "alert_view_config",
                "alert_type",
                "view_name",
                "schema_key"
        );
    }

    @Test
    void loadAlertTypeConfigs_multipleTypesShareView_resolvesCorrectly() throws SQLException {
        // Simulate 3 rows: 10000, 10001, 10002 all → credit view + credit schema
        when(resultSet.next()).thenReturn(true, true, true, false);
        when(resultSet.getString(1)).thenReturn("10000", "10001", "10002");
        when(resultSet.getString(2)).thenReturn("v_alert_json_credit", "v_alert_json_credit", "v_alert_json_credit");
        when(resultSet.getString(3)).thenReturn("credit", "credit", "credit");

        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenAnswer(inv -> {
                    ResultSetExtractor<Void> extractor = inv.getArgument(1);
                    extractor.extractData(resultSet);
                    return null;
                });

        Map<String, AlertTypeProperties> configs = repository.loadAlertTypeConfigs(SCHEMA_MAP);

        assertThat(configs).hasSize(3);
        assertThat(configs).containsKeys("10000", "10001", "10002");

        AlertTypeProperties type10000 = configs.get("10000");
        assertThat(type10000.getViewName()).isEqualTo("v_alert_json_credit");
        assertThat(type10000.getSchemaPath()).isEqualTo("schema/credit_schema.json");

        // All three share the same view and schema path
        assertThat(configs.get("10001").getViewName()).isEqualTo("v_alert_json_credit");
        assertThat(configs.get("10001").getSchemaPath()).isEqualTo("schema/credit_schema.json");
        assertThat(configs.get("10002").getViewName()).isEqualTo("v_alert_json_credit");
        assertThat(configs.get("10002").getSchemaPath()).isEqualTo("schema/credit_schema.json");
    }

    @Test
    void loadAlertTypeConfigs_differentSchemaKeys_resolveToDifferentSchemaPaths() throws SQLException {
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString(1)).thenReturn("10000", "20000");
        when(resultSet.getString(2)).thenReturn("v_alert_json_credit", "v_alert_json_equity");
        when(resultSet.getString(3)).thenReturn("credit", "equity");

        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenAnswer(inv -> {
                    ResultSetExtractor<Void> extractor = inv.getArgument(1);
                    extractor.extractData(resultSet);
                    return null;
                });

        Map<String, AlertTypeProperties> configs = repository.loadAlertTypeConfigs(SCHEMA_MAP);

        assertThat(configs.get("10000").getSchemaPath()).isEqualTo("schema/credit_schema.json");
        assertThat(configs.get("20000").getSchemaPath()).isEqualTo("schema/equity_schema.json");
        assertThat(configs.get("10000").getViewName()).isEqualTo("v_alert_json_credit");
        assertThat(configs.get("20000").getViewName()).isEqualTo("v_alert_json_equity");
    }

    @Test
    void loadAlertTypeConfigs_unknownSchemaKey_throwsIllegalStateException() throws SQLException {
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString(1)).thenReturn("10000");
        when(resultSet.getString(2)).thenReturn("v_alert_json_credit");
        when(resultSet.getString(3)).thenReturn("unknown_key");  // not in schema-map

        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenAnswer(inv -> {
                    ResultSetExtractor<Void> extractor = inv.getArgument(1);
                    extractor.extractData(resultSet);
                    return null;
                });

        assertThatThrownBy(() -> repository.loadAlertTypeConfigs(SCHEMA_MAP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown_key")
                .hasMessageContaining("schema-map");
    }

    @Test
    void loadAlertTypeConfigs_emptyTable_throwsIllegalStateException() throws SQLException {
        when(resultSet.next()).thenReturn(false);  // no rows

        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenAnswer(inv -> {
                    ResultSetExtractor<Void> extractor = inv.getArgument(1);
                    extractor.extractData(resultSet);
                    return null;
                });

        assertThatThrownBy(() -> repository.loadAlertTypeConfigs(SCHEMA_MAP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alert_view_config")
                .hasMessageContaining("no rows");
    }
}
