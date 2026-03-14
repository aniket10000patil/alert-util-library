# alert-util-library

A Spring Boot auto-configuration library that fetches alert JSON from a database view,
validates it against a JSON schema, and returns a structured result.

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
  db-name: alertDb                         # name of your DataSource bean
  view-name: v_alert_json_myapp            # DB view to query
  schema-path: schema/alert-schema.json    # classpath path to JSON schema
```

The library resolves the `DataSource` bean by name from your Spring context.
You define all DB connection details in your own `application.yml` as usual.

### 3. Ensure a DataSource bean exists with the matching name

**Single datasource example:**

```java
@Configuration
public class DataSourceConfig {

    @Bean("alertDb")
    @ConfigurationProperties("spring.datasource")
    public DataSource alertDbDataSource() {
        return DataSourceBuilder.create().build();
    }
}
```

**Multi-datasource example:**

```yaml
# application.yml
app:
  datasources:
    alertDb:
      url: jdbc:oracle:thin:@//host:1521/service
      username: myuser
      password: mypass
      hikari:
        maximum-pool-size: 5
```

```java
@Configuration
public class MultiDataSourceConfig {

    @Bean("alertDb")
    @ConfigurationProperties("app.datasources.alertDb")
    public DataSource alertDbDataSource() {
        return DataSourceBuilder.create().build();
    }
}
```

### 4. Add the schema file

Place your JSON schema on the classpath at the path specified by `schema-path`:

```
src/main/resources/schema/alert-schema.json
```

### 5. Use in your code

```java
@Service
public class MyAlertHandler {

    private final AlertService alertService;

    public MyAlertHandler(AlertService alertService) {
        this.alertService = alertService;
    }

    public void handle(String alertId) {
        AlertResult result = alertService.processAlert(alertId);
        JsonNode json = result.getJson();
        // ... use the validated JSON
    }
}
```

## How It Works

1. **Startup** — The library auto-configures by resolving the `DataSource` bean named
   `{db-name}` from your Spring context, creates a `JdbcTemplate`, and loads + compiles
   the JSON schema from `schema-path`.

2. **Runtime** — When you call `alertService.processAlert(alertId)`:
   - Queries `SELECT {json-column} FROM {view-name} WHERE {alert-id-column} = ?`
   - Handles both `VARCHAR` and `CLOB` columns transparently
   - Parses the JSON string into a `JsonNode`
   - Validates against the compiled schema
   - Returns `AlertResult` with the validated JSON

## Configuration Reference

| Property | Required | Default | Description |
|---|---|---|---|
| `alert-util.db-name` | **Yes** | — | Name of the `DataSource` bean in your app context |
| `alert-util.view-name` | **Yes** | — | DB view to query for alert JSON |
| `alert-util.schema-path` | **Yes** | — | Classpath path to the JSON schema file |
| `alert-util.alert-id-column` | No | `alert_id` | Column used to filter by alertId |
| `alert-util.json-column` | No | `alert_json` | Column holding the JSON (VARCHAR or CLOB) |

## Exceptions

| Exception | When |
|---|---|
| `AlertNotFoundException` | No row found in the DB view for the given alertId |
| `AlertProcessingException` | DB view returns malformed JSON or CLOB read failure |
| `AlertValidationException` | JSON fails schema validation (contains all error messages) |

## Project Structure

```
src/main/java/com/example/alertutil/
├── AlertUtilAutoConfiguration.java    # Auto-config entry point
├── config/
│   └── AlertUtilProperties.java       # @ConfigurationProperties binding
├── exception/
│   ├── AlertNotFoundException.java
│   ├── AlertProcessingException.java
│   └── AlertValidationException.java
├── model/
│   └── AlertResult.java               # Result DTO
├── repository/
│   └── AlertRepository.java           # DB view query + CLOB handling
├── service/
│   └── AlertService.java              # Main orchestrator
└── validator/
    └── JsonSchemaValidator.java        # Single-schema validation
```
