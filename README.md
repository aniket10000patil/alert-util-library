# alert-util-library

A Spring Boot auto-configuration library that:
1. Queries an **app-specific DB view** by `alertId` — the view handles HTML → JSON conversion
2. Parses the returned JSON string into a `JsonNode`
3. Validates it against a **classpath JSON schema**
4. Returns the validated `AlertResult` to your app

Each consuming application configures its own view name. The library code never changes.

---

## Pipeline

```
alertId
  │
  ▼
[DB View: v_alert_json_myapp]   ← HTML → JSON conversion happens HERE in SQL
  │  returns alert_json column
  ▼
[Parse JSON string → JsonNode]
  │
  ▼
[JSON Schema Validation]
  │
  ▼
AlertResult → your app
```

---

## Build & Install

```bash
cd alert-util-library
mvn clean install
```

---

## Add to Your App

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>alert-util-library</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## What You Must Provide

### 1. DataSource (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: user
    password: secret
```

### 2. DB View — must expose these two columns

```sql
CREATE VIEW v_alert_json_myapp AS
SELECT
    alert_id,
    json_build_object(           -- your HTML → JSON conversion here
        'alertId',  alert_id,
        'title',    title_col,
        'severity', severity_col
    )::text AS alert_json
FROM alerts_raw;
```

### 3. alert-util config in application.yml

```yaml
alert-util:
  view-name: v_alert_json_myapp         # REQUIRED — your app-specific DB view
  alert-id-column: alert_id             # default, override if different
  json-column: alert_json               # default, override if different
  schema-path: schema/alert-schema.json # default, override if different
```

### 4. JSON Schema on classpath

Place your schema at:
```
src/main/resources/schema/alert-schema.json
```

---

## Configuration Reference

| Property | Default | Description |
|---|---|---|
| `alert-util.view-name` | *(required)* | DB view name for this app |
| `alert-util.alert-id-column` | `alert_id` | Column used to look up by alertId |
| `alert-util.json-column` | `alert_json` | Column containing the JSON string |
| `alert-util.schema-path` | `schema/alert-schema.json` | Classpath path to JSON schema |

---

## Using AlertService in Your App

```java
@RestController
@RequestMapping("/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<JsonNode> getAlert(@PathVariable String alertId) {
        AlertResult result = alertService.processAlert(alertId);
        return ResponseEntity.ok(result.getJson());
    }
}
```

---

## Exception Handling

| Exception | When thrown |
|---|---|
| `AlertNotFoundException` | No row found in the view for the given alertId |
| `AlertValidationException` | JSON fails schema validation — has `.getValidationErrors()` |
| `AlertProcessingException` | View returned malformed/non-parseable JSON |

```java
@ExceptionHandler(AlertNotFoundException.class)
public ResponseEntity<String> handleNotFound(AlertNotFoundException e) {
    return ResponseEntity.status(404).body(e.getMessage());
}

@ExceptionHandler(AlertValidationException.class)
public ResponseEntity<Set<String>> handleValidation(AlertValidationException e) {
    return ResponseEntity.status(422).body(e.getValidationErrors());
}

@ExceptionHandler(AlertProcessingException.class)
public ResponseEntity<String> handleProcessing(AlertProcessingException e) {
    return ResponseEntity.status(500).body(e.getMessage());
}
```

---

## Project Structure

```
alert-util-library/
├── pom.xml
└── src/main/java/com/example/alertutil/
    ├── AlertUtilAutoConfiguration.java     ← wires all beans automatically
    ├── config/AlertUtilProperties.java     ← application.yml bindings
    ├── service/AlertService.java           ← inject this in your app
    ├── repository/AlertRepository.java     ← queries the DB view
    ├── validator/JsonSchemaValidator.java  ← validates JSON against schema
    ├── model/AlertResult.java              ← returned to your app
    └── exception/
        ├── AlertNotFoundException.java
        ├── AlertValidationException.java
        └── AlertProcessingException.java
```
# alert-util-library
