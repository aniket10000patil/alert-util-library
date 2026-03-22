# alert-util-library

A Spring Boot auto-configuration library that fetches alert JSON from a database view,
validates it against a JSON schema, and returns a structured result.

Each alert type has its own **DB view** and **JSON schema**, both configured once.
At runtime the calling application supplies the `alertId` and `alertTypeId` — the library
picks the right view and schema automatically.

Supports **multiple databases** — pass the db name at runtime, all views are configured once.

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>alert-util-library</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure alert types in `application.yml`

Define each alert type with its own DB view and JSON schema path.
The calling application is responsible for resolving the `alertTypeId` from the `alertId`.

```yaml
alert-util:
  alert-types:
    "10000":
      view-name: v_alert_10000              # DB view for this alert type
      schema-path: schema/10000_schema.json # JSON schema for validation
    "20000":
      view-name: v_alert_20000
      schema-path: schema/20000_schema.json
    "30000":
      view-name: v_alert_30000
      schema-path: schema/30000_schema.json
```

### 3. Add JSON schema files

Place a schema file for each alert type in `src/main/resources/` at the path specified
in `schema-path`.

```
src/main/resources/
└── schema/
    ├── 10000_schema.json
    ├── 20000_schema.json
    └── 30000_schema.json
```

### 4. Register your DataSource beans

```yaml
# application.yml — your DB connection config
app:
  datasources:
    primaryDb:
      url: jdbc:oracle:thin:@//host1:1521/service1
      username: user1
      password: pass1
    secondaryDb:
      url: jdbc:oracle:thin:@//host2:1521/service2
      username: user2
      password: pass2
```

```java
@Configuration
public class DataSourceConfig {

    @Bean("primaryDb")
    @ConfigurationProperties("app.datasources.primaryDb")
    public DataSource primaryDbDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean("secondaryDb")
    @ConfigurationProperties("app.datasources.secondaryDb")
    public DataSource secondaryDbDataSource() {
        return DataSourceBuilder.create().build();
    }
}
```

### 5. Use in your code

Your application resolves the `alertTypeId` from the `alertId` and passes both to the library.

```java
@RestController
public class AlertController {

    @Autowired
    private AlertService alertService;

    @GetMapping("/api/v1/alerts/{alertId}")
    public AlertResult getAlert(
            @PathVariable String alertId,
            @RequestParam String alertTypeId) {

        // alertTypeId resolved by the calling application (e.g. "10000", "20000")
        return alertService.processAlert("primaryDb", alertId, alertTypeId);
    }
}
```

## How It Works

1. **Startup** — The library auto-configures `AlertService`, `AlertRepository`, and
   `JsonSchemaValidator` beans. All alert-type configs are validated (view name and schema path
   must be present for every registered type). No DB connections or schema files are loaded yet.

2. **Runtime** — When you call `alertService.processAlert(dbName, alertId, alertTypeId)`:
   - Looks up the `alertTypeId` in the configured `alert-types` map → resolves `viewName` + `schemaPath`
   - Resolves the `DataSource` bean by `dbName` from the Spring context
     (cached after first lookup per dbName)
   - Queries `SELECT {json-column} FROM {viewName} WHERE {alert-id-column} = ?`
   - Handles both `VARCHAR` and `CLOB` columns transparently
   - Parses the JSON string into a `JsonNode`
   - Loads and compiles the schema from `schemaPath` (cached after first load per alert type)
   - Validates the JSON against the compiled schema
   - Returns `AlertResult` with the validated JSON

## Configuration Reference

| Property | Required | Default | Description |
|---|---|---|---|
| `alert-util.alert-types` | **Yes** | — | Map of alertTypeId → `{view-name, schema-path}` |
| `alert-util.alert-types.<id>.view-name` | **Yes** | — | DB view for this alert type |
| `alert-util.alert-types.<id>.schema-path` | **Yes** | — | Classpath path to the JSON schema file |
| `alert-util.alert-id-column` | No | `alert_id` | Column used to filter by alertId (shared across all alert types) |
| `alert-util.json-column` | No | `alert_json` | Column holding the JSON, VARCHAR or CLOB (shared across all alert types) |

## Exceptions

| Exception | When |
|---|---|
| `IllegalArgumentException` | alertTypeId not found in the configured alert-types map |
| `IllegalStateException` | No DataSource bean found for dbName, or schema file missing from classpath |
| `AlertNotFoundException` | No row found in the DB view for the given alertId |
| `AlertProcessingException` | DB view returns malformed JSON or CLOB read failure |
| `AlertValidationException` | JSON fails schema validation (contains all error messages) |

## Project Structure

```
src/main/java/com/example/alertutil/
├── AlertUtilAutoConfiguration.java    # Auto-config entry point — validates config, wires beans
├── config/
│   └── AlertUtilProperties.java       # @ConfigurationProperties — alert-types map, column names
├── exception/
│   ├── AlertNotFoundException.java
│   ├── AlertProcessingException.java
│   └── AlertValidationException.java
├── model/
│   └── AlertResult.java               # Result DTO returned to the caller
├── repository/
│   └── AlertRepository.java           # DB view query + CLOB handling (view resolved per call)
├── service/
│   └── AlertService.java              # Main orchestrator — resolves type config, fetches, validates
└── validator/
    └── JsonSchemaValidator.java        # Schema validation — lazy-loads and caches per schema path
```
