# alert-util-library

A Spring Boot auto-configuration library that fetches alert JSON from a database view,
validates it against a JSON schema, and returns a structured result.

Alert type configurations (which DB view to query, which schema to validate against) are
stored in a **DB configuration table** — not in `application.yml`. This means adding or
changing alert types requires only a DB row update, with no application redeployment.

Supports **multiple databases** and **multiple alert types** with shared views/schemas.
At runtime the calling application supplies the `dbName`, `alertInternalId`, and `alertTypeId` —
the library resolves the correct view and schema automatically.

---

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>alert-util-library</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Create the DB configuration table

The library reads alert type mappings from a table at startup (once). Create it in whichever
database you designate as the config source:

```sql
CREATE TABLE alert_view_config (
    alert_type  VARCHAR2(50)  NOT NULL,
    view_name   VARCHAR2(200) NOT NULL,
    schema_key  VARCHAR2(100) NOT NULL
);

-- Example rows: alert types 10000–10002 share one view and schema;
-- alert types 20000–20001 share a different view and schema
INSERT INTO alert_view_config VALUES ('10000', 'v_alert_json_credit', 'credit');
INSERT INTO alert_view_config VALUES ('10001', 'v_alert_json_credit', 'credit');
INSERT INTO alert_view_config VALUES ('10002', 'v_alert_json_credit', 'credit');
INSERT INTO alert_view_config VALUES ('20000', 'v_alert_json_equity', 'equity');
INSERT INTO alert_view_config VALUES ('20001', 'v_alert_json_equity', 'equity');
```

`schema_key` is a logical key that maps to a classpath schema file via `schema-map` in YAML.
Multiple alert types that share the same schema point to the same `schema_key` — no duplication.

### 3. Configure `application.yml`

```yaml
alert-util:
  view-config-db-name: configDb           # DataSource bean name that holds the config table
  view-config-table: alert_view_config    # table: (alert_type, view_name, schema_key)
  schema-map:                             # schemaKey → classpath path to JSON schema file
    credit: schema/credit_schema.json
    equity: schema/equity_schema.json

  # Optional column name overrides (defaults shown)
  alert-internal-id-column: alert_internal_id
  json-column: alert_json
  alert-type-column: alert_type
  view-name-column: view_name
  schema-key-column: schema_key
```

### 4. Add JSON schema files

Place a schema file for each distinct `schema_key` in the consuming app's
`src/main/resources/` at the path specified in `schema-map`:

```
src/main/resources/
└── schema/
    ├── credit_schema.json
    └── equity_schema.json
```

### 5. Register your DataSource beans

The library needs a named `DataSource` bean for:
- the config table (bean name = `view-config-db-name`)
- each runtime alert database (bean name passed in `processAlert`)

```yaml
# application.yml
app:
  datasources:
    configDb:
      url: jdbc:oracle:thin:@//confighost:1521/configsvc
      username: cfguser
      password: cfgpass
    primaryDb:
      url: jdbc:oracle:thin:@//host1:1521/service1
      username: user1
      password: pass1
```

```java
@Configuration
public class DataSourceConfig {

    @Bean("configDb")
    @ConfigurationProperties("app.datasources.configDb")
    public DataSource configDb() {
        return DataSourceBuilder.create().build();
    }

    @Bean("primaryDb")
    @ConfigurationProperties("app.datasources.primaryDb")
    public DataSource primaryDb() {
        return DataSourceBuilder.create().build();
    }
}
```

### 6. Call the service

```java
@RestController
public class AlertController {

    @Autowired
    private AlertService alertService;

    @GetMapping("/api/v1/alerts/{alertInternalId}")
    public AlertResult getAlert(
            @PathVariable Long alertInternalId,
            @RequestParam String alertTypeId) {

        return alertService.processAlert("primaryDb", alertInternalId, alertTypeId);
    }
}
```

---

## How It Works

### Startup (once)

1. Spring Boot detects the library on the classpath via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
2. `AlertUtilAutoConfiguration` validates required properties and resolves the `DataSource` bean named by `view-config-db-name`.
3. `ViewConfigRepository` runs one SQL query against the config table:
   ```sql
   SELECT alert_type, view_name, schema_key FROM alert_view_config
   ```
4. For each row, `schema_key` is resolved to a classpath path via `schema-map` in YAML.
5. The result is an in-memory `Map<alertTypeId, {viewName, schemaPath}>` — held for the lifetime of the application. No further DB calls are made to the config table.

### Runtime

When `alertService.processAlert(dbName, alertInternalId, alertTypeId)` is called:

1. **Resolve config** — looks up `alertTypeId` in the startup-loaded map → gets `viewName` + `schemaPath`.
2. **Resolve JdbcTemplate** — looks up the DataSource bean by `dbName` from the Spring context and wraps it in a `JdbcTemplate` (cached after first use per dbName).
3. **Query the view** — runs:
   ```sql
   SELECT {json-column} FROM {viewName} WHERE {alert-internal-id-column} = ?
   ```
   Handles both `VARCHAR`/`TEXT` and `CLOB` columns transparently.
4. **Parse JSON** — parses the raw string into a `JsonNode` via Jackson.
5. **Validate** — loads and compiles the JSON schema from `schemaPath` on the classpath (cached after first load per schema path), then validates the `JsonNode`.
6. **Return** — returns `AlertResult { alertInternalId, JsonNode }`.

---

## Configuration Reference

| Property | Required | Default | Description |
|---|---|---|---|
| `alert-util.view-config-db-name` | **Yes** | — | Name of the DataSource bean used to read the config table at startup |
| `alert-util.view-config-table` | **Yes** | — | Table containing `(alert_type, view_name, schema_key)` rows |
| `alert-util.schema-map` | **Yes** | — | Map of `schemaKey → classpath path` to JSON schema file |
| `alert-util.alert-internal-id-column` | No | `alert_internal_id` | Column used to filter by alertInternalId in alert views |
| `alert-util.json-column` | No | `alert_json` | Column holding the alert JSON (VARCHAR or CLOB) |
| `alert-util.alert-type-column` | No | `alert_type` | Column name in the config table for alert type |
| `alert-util.view-name-column` | No | `view_name` | Column name in the config table for view name |
| `alert-util.schema-key-column` | No | `schema_key` | Column name in the config table for schema key |

---

## Exceptions

| Exception | When |
|---|---|
| `IllegalArgumentException` | `alertTypeId` not found in the config table (not loaded at startup) |
| `IllegalStateException` | DataSource bean not found, schema file missing from classpath, or config table is empty/misconfigured |
| `AlertNotFoundException` | No row found in the DB view for the given `alertInternalId` |
| `AlertProcessingException` | DB view returned malformed JSON or CLOB read failure |
| `AlertValidationException` | JSON fails schema validation — exception contains all error messages |

---

## Project Structure

```
src/main/java/com/example/alertutil/
├── AlertUtilAutoConfiguration.java    # Auto-config entry point — validates config, wires beans
├── config/
│   └── AlertUtilProperties.java       # @ConfigurationProperties — schema-map, column names
├── exception/
│   ├── AlertNotFoundException.java
│   ├── AlertProcessingException.java
│   └── AlertValidationException.java
├── model/
│   └── AlertResult.java               # Result DTO returned to the caller
├── repository/
│   ├── AlertRepository.java           # DB view query + CLOB handling
│   └── ViewConfigRepository.java      # Loads alert type configs from DB table at startup
├── service/
│   └── AlertService.java              # Main orchestrator — resolves config, fetches, validates
└── validator/
    └── JsonSchemaValidator.java        # Schema validation — lazy-loads and caches compiled schemas

src/main/resources/
├── META-INF/spring/
│   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── sql/
    └── alert_json_view.sql            # Reference SQL for the Oracle alert JSON view
```
