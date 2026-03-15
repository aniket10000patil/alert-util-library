# alert-util-library

A Spring Boot auto-configuration library that fetches alert JSON from a database view,
validates it against a JSON schema, and returns a structured result.

Supports **multiple databases** — pass the db name at runtime, the view is configured once.

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
  view-name: v_alert_json                    # DB view (same across all databases)
  schema-path: schema/alert-schema.json      # classpath path to JSON schema
```

### 3. Register your DataSource beans

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

### 4. Use in your code

```java
@RestController
public class AlertController {

    @Autowired
    private AlertService alertService;

    @GetMapping("/api/v1/alerts/primary/{alertId}")
    public AlertResult getPrimaryAlert(@PathVariable String alertId) {
        return alertService.processAlert("primaryDb", alertId);
    }

    @GetMapping("/api/v1/alerts/secondary/{alertId}")
    public AlertResult getSecondaryAlert(@PathVariable String alertId) {
        return alertService.processAlert("secondaryDb", alertId);
    }
}
```

## How It Works

1. **Startup** — The library auto-configures by loading the JSON schema from
   `schema-path` and setting up the view name and column names. No database
   connections are made at startup.

2. **Runtime** — When you call `alertService.processAlert(dbName, alertId)`:
   - Resolves the `DataSource` bean by `dbName` from the Spring context
     (cached after first lookup per dbName)
   - Queries `SELECT {json-column} FROM {view-name} WHERE {alert-id-column} = ?`
   - Handles both `VARCHAR` and `CLOB` columns transparently
   - Parses the JSON string into a `JsonNode`
   - Validates against the compiled schema
   - Returns `AlertResult` with the validated JSON

## Configuration Reference

| Property | Required | Default | Description |
|---|---|---|---|
| `alert-util.view-name` | **Yes** | — | DB view to query (same across all databases) |
| `alert-util.schema-path` | **Yes** | — | Classpath path to the JSON schema file |
| `alert-util.alert-id-column` | No | `alert_id` | Column used to filter by alertId |
| `alert-util.json-column` | No | `alert_json` | Column holding the JSON (VARCHAR or CLOB) |

## Exceptions

| Exception | When |
|---|---|
| `IllegalStateException` | No DataSource bean found for the given dbName |
| `AlertNotFoundException` | No row found in the DB view for the given alertId |
| `AlertProcessingException` | DB view returns malformed JSON or CLOB read failure |
| `AlertValidationException` | JSON fails schema validation (contains all error messages) |

## Project Structure

```
src/main/java/com/example/alertutil/
├── AlertUtilAutoConfiguration.java    # Auto-config entry point
├── config/
│   └── AlertUtilProperties.java       # @ConfigurationProperties (view-name, schema-path)
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
    └── JsonSchemaValidator.java        # Single-schema validation
```
