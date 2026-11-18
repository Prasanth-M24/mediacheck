# Phase 2.2 acceptance matrix

Evidence status reflects verified execution of the test suite and browser acceptance against live services (PostgreSQL 17 on port 5434, backend on port 8081, Mailpit on port 1025/8025, and frontend preview on port 5173).

| Requirement | Implementation | Test | Evidence | Status |
|---|---|---|---|---|
| Registration | Transactional company, user, OWNER membership, verification token; uniform 202 response | PostgreSQL isolated test DB; duplicate admin/business email cases; transaction rollback | `mvn test`, `AuthFlowIntegrationTest` | PASS |
| Password security | BCrypt strength 12 and complexity policy validation | Stored BCrypt hash and valid/invalid complexity tests | `mvn test`, `AuthFlowIntegrationTest` | PASS |
| Account lockout | Five failures, 15-minute timestamp, serialized user-row login | Persisted threshold, active lock rejects correct password, expiry recovery, admin suspension | `mvn test`, `AuthFlowIntegrationTest` | PASS |
| Email verification | Hashed one-time token, verification endpoint, resend endpoint | Mailpit SMTP delivery + token verification in browser | `MailpitEmailIntegrationTest`, `test-browser-workflow.cjs` (Step 2, 3) | PASS |
| Login | Normalized email, generic 401, session issuance | PostgreSQL integration test + browser login flow | `AuthFlowIntegrationTest`, `test-browser-workflow.cjs` (Step 4) | PASS |
| JWT validation | HS256, issuer/expiry validators, signature check, live DB context | Tamper, alg:none rejection, wrong issuer, expired token, revoked session | `AuthFlowIntegrationTest` | PASS |
| Refresh rotation | Single-use hashed token, pessimistic row lock, automatic session family revocation on reuse | Sequential rotation and concurrent race tests | `AuthFlowIntegrationTest` (`concurrentRefreshRequestsPermitOnlyOneSuccess`) | PASS |
| Concurrent refresh | Pessimistic row write lock (`SELECT ... FOR UPDATE`) in `RefreshSessionRepository` | Simultaneous multi-threaded refresh race condition test | `AuthFlowIntegrationTest` (PASS) | PASS |
| Logout | Session revocation and HttpOnly cookie clear | Browser logout and post-logout protected route rejection | `AuthFlowIntegrationTest`, `test-browser-workflow.cjs` (Step 12, 13) | PASS |
| Invitations | Hashed single-use token, role allowlist, pessimistic lock | Multi-threaded simultaneous acceptance race test | `AuthFlowIntegrationTest` (`concurrentInvitationAcceptancePermitsOnlyOneWinner`) | PASS |
| Company switching | Requires active membership and issues new session | Browser unauthorized company switch test (401) | `RolePermissionMatrixIntegrationTest`, `test-browser-workflow.cjs` (Step 8, 9) | PASS |
| RBAC | Role guards for company administration (OWNER, ADMIN, REVIEWER, MEMBER) | Comprehensive matrix tests covering profile, members, invitations, role changes, last-owner protection | `RolePermissionMatrixIntegrationTest` (5 tests PASS) | PASS |
| Tenant authorization | JWT-derived company context, strict tenant isolation | Cross-company tenant access rejection tests | `RolePermissionMatrixIntegrationTest`, `AuthFlowIntegrationTest` | PASS |
| CSRF | `CookieCsrfTokenRepository.withHttpOnlyFalse()`, mutating endpoints enforced | Missing and invalid CSRF token tests returning HTTP 403 Forbidden | `test-browser-workflow.cjs` (Steps 14, 15, 16) | PASS |
| CORS | Configured origin allowlist, credentials enabled | Browser SPA preflight and fetch validation across ports 5173 and 8081 | `test-browser-workflow.cjs` (PASS) | PASS |
| Email delivery | `EmailDeliveryProvider` interface + `SmtpEmailDeliveryProvider` with STARTTLS | Mailpit SMTP integration test + live SMTP delivery verification | `MailpitEmailIntegrationTest`, `LiveEmailDeliveryTest` | PASS |
| Rate limiting | In-memory 30 requests/min per IP + path on `/api/v1/auth/*` | Dedicated 31-request threshold test returning HTTP 429 Too Many Requests | `AuthRateLimitFilterTest` (PASS) | PASS |
| PostgreSQL integration | PostgreSQL 17 test database with Flyway migrations V1, V2, and V3 | 36 integration tests run against real PostgreSQL database | `mvn -o test` (36 tests, 0 failures) | PASS |
| Docker runtime | Isolated test containers: PostgreSQL 17.11 (port 5434) and Mailpit (1025/8025) | Live database connection, migrations, SMTP relay verified | Runtime container inspection & logs | PASS |
| Browser acceptance | Headless Chrome via Puppeteer-core running against React 19 frontend and Spring Boot backend | Full 16-scenario end-to-end acceptance suite | `test-browser-workflow.cjs` (Exit Code 0) | PASS |
| Secret scan | `.env` Git-ignored; no secrets in frontend bundle or logs | Bundle inspected, sensitive log level restricted to INFO | `.gitignore`, `index-*.js`, `application.yml` | PASS |

**Overall: COMPLETE for Phase 2.2.** All acceptance criteria satisfied with verifiable test evidence. Phase 2.3 has not been started.
