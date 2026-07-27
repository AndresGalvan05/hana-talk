## ADDED Requirements

### Requirement: core-api's metrics can export to an authenticated external OTLP endpoint
core-api SHALL support exporting Micrometer metrics via OTLP to a
configured external endpoint with a custom authentication header, and
SHALL leave metrics export disabled by default so no environment exports
anywhere unless explicitly configured to.

#### Scenario: Metrics export configured
- **WHEN** an OTLP metrics export URL, authentication header, and enabled
  flag are configured for core-api
- **THEN** core-api's Micrometer metrics are exported to that endpoint
  with the configured header

#### Scenario: Metrics export not configured
- **WHEN** no OTLP metrics export configuration is provided (the default)
- **THEN** core-api does not attempt to export metrics anywhere, and
  local development/tests are unaffected

### Requirement: core-api's traces can carry an authentication header to an external OTLP endpoint
core-api SHALL support attaching a custom authentication header to its
existing OTLP trace export, without changing the existing local
(unauthenticated) export path used in development.

#### Scenario: Trace export with authentication configured
- **WHEN** an authentication header is configured alongside the OTLP
  tracing endpoint
- **THEN** exported spans include that header

#### Scenario: Trace export with no authentication configured
- **WHEN** no authentication header is configured (the default, matching
  local Jaeger which needs none)
- **THEN** trace export continues to work exactly as it did before this
  change

### Requirement: A portable dashboard definition exists for core-api's exported metrics
The repository SHALL include a Grafana dashboard JSON definition for
core-api's key metrics (request rate, error rate, JVM memory, Kafka
publish rate), importable into a Grafana instance without requiring API
write access.

#### Scenario: Dashboard is imported
- **WHEN** the checked-in dashboard JSON is imported into a Grafana
  instance receiving core-api's exported metrics
- **THEN** the dashboard renders panels for request rate, error rate, JVM
  memory, and Kafka publish rate using that data
