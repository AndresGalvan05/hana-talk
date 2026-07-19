# deployment-pipeline Specification

## Purpose
TBD - created by archiving change deploy-core-stack-k3s-oracle. Update Purpose after archive.
## Requirements
### Requirement: Multi-arch core-api images on GHCR
CI SHALL build and push `ghcr.io/andresgalvan05/hana-talk/core-api` as a
multi-arch image (`linux/amd64` and `linux/arm64`) on every push to `main`
that touches `core-api/**`, tagged with both the commit SHA and `latest`,
after lint, tests, and build pass.

#### Scenario: Merge to main
- **WHEN** a commit touching `core-api/**` lands on `main` and
  ktlintCheck/test/bootJar succeed
- **THEN** GHCR holds a manifest-list image for that SHA (and `latest`)
  resolvable on both amd64 and arm64

#### Scenario: Pull on the arm64 cluster node
- **WHEN** the k3s node (aarch64) pulls `core-api:latest`
- **THEN** the arm64 variant is selected and the container starts

### Requirement: Multi-arch frontend images on GHCR
CI SHALL build and push `ghcr.io/andresgalvan05/hana-talk/frontend` as a
multi-arch image on every push to `main` that touches `frontend/**`, tagged
with the commit SHA and `latest`, after lint and build pass. The production
image SHALL be built with `VITE_API_URL` empty so the app calls same-origin
`/api` paths.

#### Scenario: Frontend merge to main
- **WHEN** a commit touching `frontend/**` lands on `main` and lint/build pass
- **THEN** GHCR holds a multi-arch frontend image for that SHA and `latest`

#### Scenario: Production API base
- **WHEN** the frontend image is served and a user logs in
- **THEN** the browser requests `/api/...` on the page's own origin (no
  cross-origin request, no CORS preflight)

### Requirement: Public image pull without credentials
Both GHCR packages SHALL be public so the cluster pulls images without
`imagePullSecrets`.

#### Scenario: Fresh cluster pull
- **WHEN** a pod referencing a GHCR image is scheduled on a cluster with no
  registry credentials configured
- **THEN** the image pull succeeds

### Requirement: Documented manual deploy step
Deploying a newly pushed image SHALL be a single documented `kubectl` command
(rollout restart), with the reproducibility trade-off of `:latest` deploys and
the digest-pinning alternative recorded in the runbook.

#### Scenario: Ship a change
- **WHEN** CI has pushed a new `latest` image and the operator runs the
  documented rollout-restart command
- **THEN** the running deployment picks up the new image
  (`imagePullPolicy: Always`) and becomes Ready

#### Scenario: Roll back a bad deploy
- **WHEN** a rollout introduces a regression
- **THEN** the runbook's rollback command (`kubectl rollout undo` or re-deploy
  of a prior SHA tag) restores the previous working version

