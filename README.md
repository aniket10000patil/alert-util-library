# alert-util-library

A Spring Boot auto-configuration library that fetches alert JSON from a database view,
validates it against a JSON schema, and returns a structured result.

<<<<<<< HEAD
Supports **multiple databases** — pass the db name at runtime, the view is configured once.

## Quick Start

=======
## Quick Start

>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
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
<<<<<<< HEAD
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
=======
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
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3

    @Autowired
    private AlertService alertService;

<<<<<<< HEAD
    @GetMapping("/api/v1/alerts/primary/{alertId}")
    public AlertResult getPrimaryAlert(@PathVariable String alertId) {
        return alertService.processAlert("primaryDb", alertId);
    }

    @GetMapping("/api/v1/alerts/secondary/{alertId}")
    public AlertResult getSecondaryAlert(@PathVariable String alertId) {
        return alertService.processAlert("secondaryDb", alertId);
=======
    public MyAlertHandler(AlertService alertService) {
        this.alertService = alertService;
    }

    public void handle(String alertId) {
        AlertResult result = alertService.processAlert(alertId);
        JsonNode json = result.getJson();
        // ... use the validated JSON
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
    }
}
```

## How It Works

<<<<<<< HEAD
1. **Startup** — The library auto-configures by loading the JSON schema from
   `schema-path` and setting up the view name and column names. No database
   connections are made at startup.

2. **Runtime** — When you call `alertService.processAlert(dbName, alertId)`:
   - Resolves the `DataSource` bean by `dbName` from the Spring context
     (cached after first lookup per dbName)
=======
1. **Startup** — The library auto-configures by resolving the `DataSource` bean named
   `{db-name}` from your Spring context, creates a `JdbcTemplate`, and loads + compiles
   the JSON schema from `schema-path`.

2. **Runtime** — When you call `alertService.processAlert(alertId)`:
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
   - Queries `SELECT {json-column} FROM {view-name} WHERE {alert-id-column} = ?`
   - Handles both `VARCHAR` and `CLOB` columns transparently
   - Parses the JSON string into a `JsonNode`
   - Validates against the compiled schema
   - Returns `AlertResult` with the validated JSON

## Configuration Reference

| Property | Required | Default | Description |
|---|---|---|---|
<<<<<<< HEAD
| `alert-util.view-name` | **Yes** | — | DB view to query (same across all databases) |
=======
| `alert-util.db-name` | **Yes** | — | Name of the `DataSource` bean in your app context |
| `alert-util.view-name` | **Yes** | — | DB view to query for alert JSON |
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
| `alert-util.schema-path` | **Yes** | — | Classpath path to the JSON schema file |
| `alert-util.alert-id-column` | No | `alert_id` | Column used to filter by alertId |
| `alert-util.json-column` | No | `alert_json` | Column holding the JSON (VARCHAR or CLOB) |

## Exceptions

| Exception | When |
|---|---|
<<<<<<< HEAD
| `IllegalStateException` | No DataSource bean found for the given dbName |
=======
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
| `AlertNotFoundException` | No row found in the DB view for the given alertId |
| `AlertProcessingException` | DB view returns malformed JSON or CLOB read failure |
| `AlertValidationException` | JSON fails schema validation (contains all error messages) |

## Project Structure

```
src/main/java/com/example/alertutil/
├── AlertUtilAutoConfiguration.java    # Auto-config entry point
├── config/
<<<<<<< HEAD
│   └── AlertUtilProperties.java       # @ConfigurationProperties (view-name, schema-path)
=======
│   └── AlertUtilProperties.java       # @ConfigurationProperties binding
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
├── exception/
│   ├── AlertNotFoundException.java
│   ├── AlertProcessingException.java
│   └── AlertValidationException.java
├── model/
│   └── AlertResult.java               # Result DTO
├── repository/
│   └── AlertRepository.java           # DB view query + CLOB handling
├── service/
<<<<<<< HEAD
│   └── AlertService.java              # Main orchestrator (multi-db, cached JdbcTemplates)
=======
│   └── AlertService.java              # Main orchestrator
>>>>>>> 58191668cdb2e3dab86ff08ffef034712a2582f3
└── validator/
    └── JsonSchemaValidator.java        # Single-schema validation
```
