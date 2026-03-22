# alert-util-library

A Spring Boot auto-configuration library that fetches alert JSON from a database view,
validates it against a per-alert-type JSON schema, and returns a structured result.

Supports **multiple databases** — pass the db name and schema at runtime, the view is configured once.
Supports **multiple alert types** — pass the alert type id at runtime, the correct schema is selected automatically.

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>alert-util-library</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure in `application.yml`

```yaml
alert-util:
  view-name: v_alert_json        # DB view (same across all databases)
  schema-base-path: schema       # classpath directory containing per-alert-type schema files
```

### 3. Add per-alert-type schema files

Place a JSON schema file for each alert type in `src/main/resources/schema/`.
Files must follow the naming convention `{alertTypeId}_schema.json`.

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

Your application is responsible for resolving the `alertTypeId` before calling the library.
Pass it alongside the `alertId` — the library uses it to select the correct schema.

```java
@RestController
public class AlertController {

    @Autowired
    private AlertService alertService;

    @GetMapping("/api/v1/alerts/{alertId}")
    public AlertResult getAlert(
            @PathVariable String alertId,
            @RequestParam String alertTypeId) {

        // Your application resolves dbName and schema (e.g. from config, headers, etc.)
        return alertService.processAlert("primaryDb", "mySchema", alertId, alertTypeId);
    }
}
```

## How It Works

1. **Startup** — The library auto-configures by registering `AlertService`, `AlertRepository`,
   and `JsonSchemaValidator` beans. No database connections or schema files are loaded at startup.

2. **Runtime** — When you call `alertService.processAlert(dbName, schema, alertId, alertTypeId)`:
   - Resolves the `DataSource` bean by `dbName` from the Spring context
     (cached after first lookup per dbName)
   - Queries `SELECT {json-column} FROM {schema}.{view-name} WHERE {alert-id-column} = ?`
   - Handles both `VARCHAR` and `CLOB` columns transparently
   - Parses the JSON string into a `JsonNode`
   - Loads and compiles the schema for `alertTypeId` from `{schema-base-path}/{alertTypeId}_schema.json`
     (cached after first load per alertTypeId)
   - Validates the JSON against the compiled schema
   - Returns `AlertResult` with the validated JSON

## Configuration Reference

| Property | Required | Default | Description |
|---|---|---|---|
| `alert-util.view-name` | **Yes** | — | DB view to query (same across all databases) |
| `alert-util.schema-base-path` | **Yes** | — | Classpath directory containing `{alertTypeId}_schema.json` files |
| `alert-util.alert-id-column` | No | `alert_id` | Column used to filter by alertId |
| `alert-util.json-column` | No | `alert_json` | Column holding the JSON (VARCHAR or CLOB) |

## Exceptions

| Exception | When |
|---|---|
| `IllegalStateException` | No DataSource bean found for the given dbName, or schema file not found on classpath |
| `AlertNotFoundException` | No row found in the DB view for the given alertId |
| `AlertProcessingException` | DB view returns malformed JSON or CLOB read failure |
| `AlertValidationException` | JSON fails schema validation (contains all error messages) |

## Project Structure

```
src/main/java/com/example/alertutil/
├── AlertUtilAutoConfiguration.java    # Auto-config entry point
├── config/
│   └── AlertUtilProperties.java       # @ConfigurationProperties (view-name, schema-base-path)
├── exception/
│   ├── AlertNotFoundException.java
│   ├── AlertProcessingException.java
│   └── AlertValidationException.java
├── model/
│   └── AlertResult.java               # Result DTO
├── repository/
│   └── AlertRepository.java           # DB view query + CLOB handling
├── service/
│   └── AlertService.java              # Main orchestrator (multi-db, cached JdbcTemplates)
└── validator/
    └── JsonSchemaValidator.java        # Per-alert-type schema validation (lazy-loaded, cached)
```
