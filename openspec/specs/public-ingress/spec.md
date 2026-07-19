# public-ingress Specification

## Purpose
TBD - created by archiving change deploy-core-stack-k3s-oracle. Update Purpose after archive.
## Requirements
### Requirement: Single-host routing at hanatalk.online
A standard Kubernetes Ingress on the bundled Traefik SHALL route
`hanatalk.online` traffic by path: `/api` prefix to the core-api Service,
all other paths to the frontend Service.

#### Scenario: App loads
- **WHEN** a browser opens `https://hanatalk.online/` or any client-side
  route (e.g. `/courses/<id>`) directly
- **THEN** the SPA is served (nginx falls back to `index.html`) and renders

#### Scenario: API calls routed
- **WHEN** the SPA calls `/api/auth/login` on its own origin
- **THEN** the request reaches core-api and behaves exactly as it does locally

### Requirement: End-to-end TLS via Cloudflare Full (strict)
DNS for `hanatalk.online` SHALL be Cloudflare-proxied with SSL mode
Full (strict); the origin leg SHALL be encrypted using a Cloudflare Origin CA
certificate stored as a manually-created `kubernetes.io/tls` Secret referenced
by the Ingress.

#### Scenario: Public HTTPS
- **WHEN** a browser connects to `https://hanatalk.online`
- **THEN** it gets a valid publicly-trusted certificate and the
  Cloudflare→origin leg is also TLS (no Flexible-mode plaintext hop)

#### Scenario: Plain HTTP request
- **WHEN** a client requests `http://hanatalk.online`
- **THEN** it is redirected to HTTPS

### Requirement: M1 user flow works publicly
The complete M1 vertical slice SHALL work for an anonymous visitor at
`https://hanatalk.online` with no CORS errors and no localhost assumptions.

#### Scenario: Full flow from a fresh browser
- **WHEN** a new visitor registers, logs in, opens "JLPT N5: First Steps in
  Japanese", reads a lesson, and marks it complete
- **THEN** the checkmark and progress bar update, and the browser console
  shows no CORS or mixed-content errors throughout

