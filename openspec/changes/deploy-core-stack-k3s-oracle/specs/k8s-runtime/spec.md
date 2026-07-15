# k8s-runtime

## ADDED Requirements

### Requirement: Full core stack runs in the `hanatalk` namespace
The cluster SHALL run postgres, Kafka, core-api, and frontend in a single
`hanatalk` namespace, defined entirely by manifests under `infra/k8s/`, with
resource requests/limits sized to fit the 4 OCPU / 24 GB node.

#### Scenario: Apply manifests to a fresh cluster
- **WHEN** the manifests are applied in the runbook's documented order on a
  provisioned cluster (with secrets already created)
- **THEN** all pods reach Ready, and core-api serves authenticated API traffic
  backed by the seeded Flyway data (N5 course and lessons present)

#### Scenario: Node restart
- **WHEN** the VM reboots
- **THEN** all workloads return to Ready without manual intervention and
  previously registered users can still log in

### Requirement: Stateful services keep data across pod restarts
Postgres and Kafka SHALL run as single-replica StatefulSets with
PersistentVolumeClaims on the `local-path` StorageClass; user and progress
data SHALL survive pod deletion and rescheduling.

#### Scenario: Postgres pod replaced
- **WHEN** the postgres pod is deleted and recreated by its StatefulSet
- **THEN** existing users, courses, and progress rows are intact

#### Scenario: Kafka pod replaced
- **WHEN** the kafka pod is deleted and recreated
- **THEN** the broker rejoins with the same cluster metadata (no
  re-format/ID-mismatch crash loop) and core-api resumes publishing

### Requirement: Kafka runs single-node KRaft, cluster-internal only
Kafka SHALL run `apache/kafka` in KRaft combined mode with one broker,
reachable only inside the cluster as `kafka:9092`; events published by
core-api SHALL be consumable in-cluster.

#### Scenario: Event spot-check
- **WHEN** a user registers or completes a lesson through the public site and
  the operator runs the documented in-cluster console-consumer command
- **THEN** the corresponding event appears on the expected topic

#### Scenario: Kafka down, best-effort preserved
- **WHEN** the kafka pod is unavailable and a user completes a lesson
- **THEN** the API request still succeeds (publishing stays best-effort per
  existing design; the manifests introduce no change to this behavior)

### Requirement: Configuration via ConfigMaps, credentials via manual Secrets
Non-sensitive configuration SHALL live in committed ConfigMaps; credentials
(`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`) SHALL live in k8s Secrets created
manually per the runbook, with only placeholder templates committed. OTel
export SHALL be disabled (`OTEL_SAMPLING_PROBABILITY=0.0`) until M5.

#### Scenario: No secrets in git
- **WHEN** the repository is searched for credential values
- **THEN** only placeholder/template files with creation instructions are
  found, and applying committed manifests alone (without the runbook's secret
  step) leaves core-api pending on missing Secret references rather than
  running with defaults

#### Scenario: No tracing noise
- **WHEN** core-api handles traffic in the cluster
- **THEN** logs contain no OTLP export-failure errors

### Requirement: Probes gate traffic on real readiness
core-api SHALL expose liveness and readiness probes backed by Spring actuator
health endpoints that are reachable only inside the cluster; frontend SHALL
have an HTTP probe on its nginx server.

#### Scenario: Slow JVM startup
- **WHEN** core-api is starting (JVM + Flyway migrations)
- **THEN** the Service sends it no traffic until the readiness probe passes,
  and the pod is not liveness-killed during normal startup

#### Scenario: Actuator not public
- **WHEN** `https://hanatalk.online/actuator/health` is requested from the
  internet
- **THEN** the response is not the actuator payload (path is not routed by the
  ingress)
