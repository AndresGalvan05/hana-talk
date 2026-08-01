## MODIFIED Requirements

### Requirement: Users have a role of USER or ADMIN, defaulting to USER
The system SHALL assign every user a role of `USER` or `ADMIN`, and
self-registration SHALL always assign `USER` regardless of any request
content. A user's own role SHALL be included in their profile response
so that clients can determine it without a direct database query.

#### Scenario: A user's profile includes their role
- **WHEN** an authenticated user requests their own profile
- **THEN** the response includes their current role (`USER` or `ADMIN`)
