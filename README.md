# alert-util-library

A Spring Boot auto-configuration library that fetches alert JSON from a database view,
validates it against JSON schemas, and returns a structured result.

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

Only **two parameters** are required — which database to use and which view to query:

```yaml
alert-util:
  db-name: alertDb                       # name of your DataSource bean
  view-name: v_alert_json_myapp          # DB view to query

  # Schema validation (required)
  schema-map:
    10000: schema/schema-10000.json
    20000: schema/schema-20000.json
```

The library resolves the `DataSource` bean by name from your Spring context.
You define all DB connection details in your own `application.yml` as usual.

### 3. Ensure a DataSource bean exists with the matching name

The consuming app must register a `DataSource` bean whose name matches `db-name`.

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

**Multi-datasource example** (your app already has multiple datasources):

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
    otherDb:
      url: jdbc:oracle:thin:@//host2:1521/service2
      ...
```

```java
@Configuration
public class MultiDataSourceConfig {

    @Bean("alertDb")
    @ConfigurationProperties("app.datasources.alertDb")
    public DataSource alertDbDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean("otherDb")
    @ConfigurationProperties("app.datasources.otherDb")
    public DataSource otherDbDataSource() {
        return DataSourceBuilder.create().build();
    }
}
```

### 4. Use in your code

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
   `{db-name}` from your Spring context, creates a `JdbcTemplate`, loads and compiles
   all JSON schemas from `schema-map`.

2. **Runtime** — When you call `alertService.processAlert(alertId)`:
   - Queries `SELECT {json-column} FROM {view-name} WHERE {alert-id-column} = ?`
   - Handles both `VARCHAR` and `CLOB` columns transparently
   - Parses the JSON string into a `JsonNode`
   - Extracts the alert type field, validates against the matching schema
   - Returns `AlertResult` with the validated JSON

## Configuration Reference

| Property | Required | Default | Description |
|---|---|---|---|
| `alert-util.db-name` | **Yes** | — | Name of the `DataSource` bean in your app context |
| `alert-util.view-name` | **Yes** | — | DB view to query for alert JSON |
| `alert-util.schema-map` | **Yes** | — | Map of alertType → classpath schema file path |
| `alert-util.alert-id-column` | No | `alert_id` | Column used to filter by alertId |
| `alert-util.json-column` | No | `alert_json` | Column holding the JSON (VARCHAR or CLOB) |
| `alert-util.alert-type-field` | No | `alertType` | JSON field name that holds the alert type |

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
    └── JsonSchemaValidator.java        # Multi-schema validation
```
