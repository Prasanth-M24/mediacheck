# Phase 2.2 authentication and security

## Status

Implementation is present in the existing Spring Boot and React applications. The latest local Maven suite passes 22 tests on H2, the backend package succeeds, and direct frontend TypeScript, ESLint, and production Vite build checks pass. This phase is **PARTIALLY COMPLETE**: this session could not access Docker Engine, so isolated PostgreSQL 17.11 authentication integration, Compose runtime, and browser acceptance remain unverified. SMTP adapter code is present, but live delivery credentials are unavailable. Business repositories also do not yet have service/API coverage for future rights, media, policy, report, or audit workflows. Do not treat this as production-ready or complete tenant isolation. Detailed evidence is in [phase-2.2-verification.md](phase-2.2-verification.md).

## Implementation plan and schema inspection

Phase 2.1 uses `companies`, `users`, `company_memberships`, and `audit_events`; user statuses are constrained by V1 to `ACTIVE`, `INVITED`, and `SUSPENDED`, and company memberships carry `OWNER`, `ADMIN`, `REVIEWER`, or `MEMBER`. Company registration details, password/verification/lock data, refresh sessions, invitations, and one-time account tokens were missing. This implementation adds V2 and V3 only; V1 remains unchanged.

| Migration | Changes |
| --- | --- |
| `V2__authentication_and_membership_security.sql` | Adds company legal/contact fields, nullable password hash for compatibility with existing users, email verification and login lock metadata; adds refresh session, company invitation, and account action token tables, foreign keys, unique token hashes, and supporting indexes. Adds `(company_id,id)` membership uniqueness for tenant-aware session/inviter FKs. |
| `V3__authentication_uniqueness_indexes.sql` | PostgreSQL partial unique indexes for normalized company business emails and one pending invitation per company/email. |

PostgreSQL verification ran on the existing PostgreSQL 17.11 container and persistent `infrastructure_postgres-data` volume. Before migration, Flyway contained only V1 checksum `-1415117782`; V1 was not changed. V2 and V3 applied successfully. Hibernate `ddl-auto: validate` initialized successfully; `/actuator/health` returned `UP`. Existing company, user, membership, and audit row counts remained zero. No volume was removed or reset. H2 tests target V2 because V3 deliberately uses PostgreSQL-specific expression/partial indexes; V3 was applied and inspected on PostgreSQL.

## Registration and authentication flows

Company signup accepts company name, optional legal/contact/website information, business email, and initial administrator details. Email addresses are normalized to lowercase. The server generates the slug and assigns the initial `OWNER`; clients cannot submit a trusted company ID, role, or status. Company, user, membership, verification token, and audit event are written transactionally. Passwords use BCrypt strength 12. Policy: 12–72 characters, upper and lower case, digit, symbol, and at most 72 UTF-8 bytes.

New accounts use the existing `INVITED` status until email verification. Verification and reset tokens are cryptographically random, single-use, expiring, and stored only as SHA-256 hashes. The configured deployment has no email provider. In the `local` profile only, development token delivery is enabled so a developer can explicitly verify or reset an account. Production must use a delivery integration; it does not return verification, invitation, or reset secrets.

Login normalizes email, serializes credential checks by locking the user row, returns the same generic 401 for unknown email and bad password, checks verification/status, and requires company selection when multiple active memberships exist. Five failed password checks suspend the account for 15 minutes; only accounts with the failed-login threshold and an expired lock are automatically restored, preserving administrative suspensions. A successful login records a security audit event and issues a short-lived access token plus an opaque refresh token.

## JWT and refresh-token lifecycle

- Access tokens are signed and decoded through Spring Security Nimbus OAuth2 Resource Server, using HS256 only. The Base64 signing secret must be at least 256 bits; it is required at startup and is never in the frontend bundle.
- Claims: issuer, subject (user UUID), issue/expiry time, token ID, company UUID, membership UUID, active role, and refresh-session UUID. Passwords, tokens, licence contents, and storage credentials are excluded.
- Default access-token lifetime is 600 seconds. Refresh lifetime defaults to 14 days.
- Refresh secrets are random 256-bit values, delivered only in an HttpOnly, SameSite=Strict cookie scoped to `/api/v1/auth`; the database stores only SHA-256 hashes. Successful refresh rotates the cookie and revokes the old session. Reuse of a revoked token revokes that user’s remaining sessions.
- The frontend keeps access tokens in JavaScript memory, not localStorage. Logout revokes the current session; logout-all revokes all refresh sessions. Session IDs are checked for every bearer request, so logout and company switching invalidate the previous access token immediately.
- Cookie-authenticated mutations require CSRF tokens. CORS permits only configured origins and credentials. Production (`prod` profile or `APP_ENV=production`) requires secure refresh cookies and disables development token delivery.

Generate a local key without displaying it, then add it to ignored `.env` as `MEDIACHECK_JWT_SECRET`:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Set `MEDIACHECK_JWT_SECRET` to the generated value. For production, also set `SPRING_PROFILES_ACTIVE=prod`, `APP_ENV=production`, `MEDIACHECK_REFRESH_COOKIE_SECURE=true`, `MEDIACHECK_DEV_TOKEN_DELIVERY=false`, and the correct issuer/CORS origin. Do not pass the signing key to Vite or commit `.env`.

## Invitation and membership model

Owners/admins can invite normalized emails with `ADMIN`, `REVIEWER`, or `MEMBER`; `OWNER` cannot be assigned through an invitation. Invitation tokens are random, single-use, expire after seven days, and are stored hashed. Acceptance creates or links the invited identity and an active membership in one transaction. In development the token is returned in the response because no email transport exists; this behavior is disabled in production.

Membership is checked against the database for each request. The server derives the active company from signed claims, checks user verification/status, company status, membership status, membership ID, and current role. Switching company requires an active membership and issues a new session. Role or membership changes invalidate the old token/session. The last active owner cannot be demoted or revoked; only an owner can grant the owner/admin roles.

| Permission | Owner | Admin | Reviewer | Member |
| --- | --- | --- | --- | --- |
| Read own company profile | Yes | Yes | Yes | Yes |
| Update company profile | Yes | No | No | No |
| List members | Yes | Yes | Yes | No |
| Invite/revoke invitations | Yes | Yes, no owner/admin invite | No | No |
| Change member roles/status | Yes, owner protections | Regular members only; cannot grant admin/owner | No | No |
| Platform-wide administration | No implied access | No | No | No |

No `PLATFORM_ADMIN` role exists. Authentication and company-administration endpoints are implemented; future business APIs must still apply company-scoped repository/service authorization before exposing company-owned records. Existing generic `JpaRepository` methods remain unrestricted to trusted server code. Do not claim complete tenant isolation for future media, rights, licence, policy, decision, report, or audit APIs.

## API inventory

Public authentication endpoints: `POST /api/v1/auth/register-company`, `/login`, `/refresh`, `/accept-invitation`, `/forgot-password`, `/reset-password`, `/verify-email`, `/resend-verification`; `GET /api/v1/auth/csrf` initializes CSRF state.

Authenticated endpoints: `GET /api/v1/auth/me`, `GET /api/v1/auth/companies`, `POST /api/v1/auth/switch-company`, `/logout`, and `/logout-all`.

Company endpoints: `GET`/`PUT /api/v1/company/profile`, `GET /api/v1/company/members`, `POST`/`GET /api/v1/company/invitations`, `DELETE /api/v1/company/invitations/{id}`, `PATCH /api/v1/company/members/{membershipId}/role`, and `PATCH /api/v1/company/members/{membershipId}/status`.

Responses omit password hashes and raw stored tokens. Authentication failures are generic; validation, conflict, and authorization errors use safe API error bodies. Rate limiting applies to auth routes: 30 requests per IP/path per minute in this single instance, plus login lockout. The limiter is in-memory and does not coordinate across replicas.

## Email delivery configuration

The backend includes a Spring Mail SMTP adapter for verification, company invitation, and password reset links. Messages are sent after the surrounding transaction commits. The adapter does not log token or recipient content. Mocked contract tests verify link generation and disabled delivery.

Provide these backend-only variables in deployment: `MEDIACHECK_EMAIL_ENABLED=true`, `MEDIACHECK_EMAIL_FROM`, `MEDIACHECK_PUBLIC_APP_URL`, `MEDIACHECK_SMTP_HOST`, `MEDIACHECK_SMTP_PORT`, `MEDIACHECK_SMTP_USERNAME`, and `MEDIACHECK_SMTP_PASSWORD`. SMTP authentication and STARTTLS default to enabled. Production startup rejects missing authenticated STARTTLS configuration, insecure refresh cookies, HTTP issuer URLs, or development token delivery. Do not pass SMTP variables to Vite. Live delivery is unverified until a real provider is configured and tested.

## Frontend integration

The existing React layout now has login, company registration, invitation acceptance, and password reset pages; protected application routes; session restoration/expiry handling; company selector; and logout. Requests use credentials and CSRF headers. The backend remains authoritative for permissions. Existing business pages remain the Phase 1 placeholders and are not redesigned.

## Security testing and verification

| Check | Result |
| --- | --- |
| Backend Maven suite | Latest result: 22 tests passed, 0 failures/errors/skips. H2, Flyway target V2; includes forced transaction rollback, duplicate rejection, explicit verification, BCrypt, generic login errors, lockout persistence/expiry/reset and administrative suspension preservation, JWT validation, refresh rotation/reuse, logout, invitation acceptance/expiry/replay, company switching, role restrictions, membership revocation, CSRF rejection, and mocked SMTP contract checks. Not PostgreSQL evidence. |
| Backend package | `mvn -o -DskipTests package` succeeded. |
| Frontend | Direct TypeScript `tsc -b`, ESLint, and Vite production build passed. `npm run` launch failed because Node could not resolve npm under restricted `C:\Users\prasa`; see final verification report. |
| PostgreSQL/Compose/browser | Not rerun: Docker Engine pipe access returned permission denied. Earlier PostgreSQL 17.11 migration/runtime smoke results are recorded above but do not substitute for isolated auth tests. |

No Phase 0 contracts were edited. This workspace has no Git history, so byte-for-byte historical preservation cannot be proven from Git. No media-analysis, rights workflow, policy evaluation, or Phase 2.3 feature was started.

## Remaining limitations

- Spring Mail SMTP delivery for verification, invitations, and reset is implemented and mocked-contract tested. No real provider credentials are available, so live delivery is unverified. Production startup fails closed without authenticated SMTP configuration. Domain verification and delivery retries/monitoring remain unimplemented.
- Registration now returns the same generic 400 code for duplicate user or company details; detailed timing and non-registration company business-email inference have not received an external review.
- Rate limiting is process-local; use a shared store or gateway policy for multi-instance deployment.
- No deployment-grade secret manager, key rotation, backup/restore, abuse monitoring, or external security review has been performed.
- The existing business repositories expose generic operations. Their future endpoints must use company-scoped services and cross-company read/write tests.
- PostgreSQL runtime migration and Hibernate validation were verified in the prior phase; the full auth suite in this phase was H2-targeted. No simultaneous refresh request test or real database race test was run.
- Isolated PostgreSQL authentication, Compose rebuild/service connectivity, and UI browser-level flows remain acceptance checks; Docker Engine access was denied in the current environment.

**Current verdict: PARTIALLY COMPLETE.** Do not start Phase 2.3 until PostgreSQL, Compose and browser acceptance run successfully, an email provider is configured, and remaining service-level tenant enforcement is reviewed.
