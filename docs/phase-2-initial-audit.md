# Phase 2 Initial Repository Audit

## Current implementation follow-up — 2026-11-18

The original audit is retained as the Phase 2 baseline. Since that audit, the repository now contains the following completed implementation work:

- V4 now adds company-aware composite foreign keys for registry membership references. V1, V2, and V3 were not modified.
- `LicenceDocument` has complete persistence fields, constructor, accessors, and creation timestamp mapping.
- Registry status mappings include the V4 status vocabulary.
- H2 remains a V2 compatibility suite because V3 uses PostgreSQL expression/partial indexes. PostgreSQL is the authoritative V1–V4 validation target.
- Added company-scoped `RightsRegistryService` and `RightsRegistryController` for rights assets, asset files, licences, licence documents, lifecycle status, dashboard counts, and audit events.
- Added RBAC enforcement: all authenticated company roles can read registry data; only `OWNER` and `ADMIN` can write or archive it.
- Hardened local storage with empty-file/size checks, path traversal protection, SHA-256 calculation, and atomic temporary-file replacement.
- Added persistent `mediacheck-data` storage to the backend Compose service.
- Added frontend registry API functions, multipart upload support, dashboard metrics, search, detail/edit/archive/status flows, document listing/download/delete, and role-aware controls.
- Backend regression suite: 41 tests, 0 failures, 0 errors, 5 environment-gated skips. Frontend TypeScript, ESLint, and Vite production build pass. The Maven wrapper was repaired and runs successfully.

PostgreSQL V4 migration, Hibernate validation, two-company registry isolation, Docker rebuild/runtime checks, Mailpit email capture, and live service connectivity have passed. Browser acceptance passed registration, verification, login, restoration, registry navigation, invitation acceptance, password reset, logout, protected-route redirection, and invalid-CSRF rejection. The final Phase 2 verdict is **COMPLETE** for the approved implementation scope.

**Date:** 2026-11-10  
**Scope:** Stage 0 complete repository, schema, security, storage, API, frontend, and infrastructure audit prior to Phase 2 completion.

---

## 1. Repository & Environment Snapshot

- **Workspace:** `d:\4th Year\8th Sem\MediaCheck1` (no `.git` directory present).
- **Existing Services / Modules:**
  - `backend/`: Spring Boot 3.4.5, Java 21, Spring Security OAuth2 Resource Server (JWT), Spring Data JPA / Hibernate (`ddl-auto: validate`), Flyway.
  - `frontend/`: React 19, TypeScript 5.8, Vite 6, Tailwind CSS 4, React Router 7.
  - `ai-service/`: Python FastAPI with `/health/live`, `/health/ready`, `/api/v1/info` (2/2 pytest tests passing).
  - `infrastructure/`: `docker-compose.yml` defining `frontend`, `backend`, `ai-service`, `postgres` (`postgres:17-alpine`), `rabbitmq` (`rabbitmq:4-management-alpine`).
  - `contracts/`: Shared JSON schemas from Phase 0.
- **Database State Inspected:**
  - `infrastructure-postgres-1` (PostgreSQL 17.11, host port `5433`, DB `mediacheck`): Flyway `V1`, `V2`, `V3` applied (`success = t`). Existing preserved data: 1 company, 1 user, 1 membership, 4 audit events, 0 registry records.
  - `mediacheck-test-postgres` (PostgreSQL 17.11, host port `5434`, DB `mediacheck_isolated_test`): Flyway `V1`, `V2`, `V3` applied (`success = t`).
  - `V4__rights_and_licence_registry_enhancements.sql` exists on disk but has **not** been applied to either PostgreSQL instance.

---

## 2. Baseline Verification Results (Pre-Edit)

| Check | Command | Result | Root Cause |
|---|---|---|---|
| AI Service Tests | `python -m pytest -q` | **PASS** (2 passed) | Clean |
| Frontend TypeScript | `tsc -b` | **PASS** (0 errors) | Clean |
| Frontend ESLint | `eslint .` | **FAIL** (1 error) | `frontend/src/services/api.ts:11` empty `catch {}` block (`no-empty`) |
| Backend Maven Tests (H2) | `.\mvnw.cmd -o test` | **FAIL** (37 run, 27 errors, 2 skipped) | `RightsAsset`, `LicenceRecord`, `LicenceDocument` include V4 fields while `application-test.yml` pins `spring.flyway.target: 2` (`Schema-validation: missing column [file_size] in table [licence_documents]`) |

---

## 3. Requirement Classification Matrix

### Phase 2.1 — Database and Domain Foundation
| Requirement | Classification | Evidence & Notes |
|---|---|---|
| Core PostgreSQL schema (V1) | **COMPLETE** | `V1__create_core_domain_schema.sql` applied on PostgreSQL 17.11 (`19` domain tables, `35` FKs, composite tenant FKs). |
| Auth & membership schema (V2, V3) | **COMPLETE** | `V2` and `V3` applied cleanly in `flyway_schema_history`. |
| Domain entities & relationships | **PARTIAL** | Core entities exist; `RightsAsset` missing `@JdbcTypeCode(SqlTypes.CHAR)` on `sha256Checksum`; `LicenceDocument` has V4 fields without getters/setters/constructors/`createdAt`; `RecordStatus` enum vs V4 check constraints need alignment. |
| Repositories & tenant-scoped queries | **PARTIAL** | Auth repositories verified; `RightsAssetRepository`, `LicenceRecordRepository`, `LicenceDocumentRepository` exist but null-search JPQL parameters need PostgreSQL type-safety validation. |
| Hibernate schema validation | **PARTIAL** | Broken on H2/test profile due to unapplied V4 mismatch (`flyway.target: 2`); must be validated against real PostgreSQL 17 with V4. |

### Phase 2.2 — Authentication, Authorization and Multi-Tenant Security
| Requirement | Classification | Evidence & Notes |
|---|---|---|
| Company & user registration | **COMPLETE** | `AuthService.registerCompany` + `AuthController` (needs PostgreSQL regression re-run after V4 fix). |
| Password hashing & policy | **COMPLETE** | BCrypt strength 12, 12–72 chars complexity check. |
| Login, lockout & JWT | **COMPLETE** | Row lock on login, 5-attempt lockout, HS256 JWT with issuer/expiry/live membership/session validation in `SecurityConfiguration`. |
| Refresh token rotation & logout | **COMPLETE** | Single-use hashed refresh tokens with `SELECT ... FOR UPDATE`, reuse family revocation, HttpOnly cookie, logout & logout-all. |
| Email verification & password reset | **COMPLETE** | `AccountActionToken` hashed tokens, `AccountEmailService` + `SmtpEmailDeliveryProvider`. |
| Company membership & switching | **COMPLETE** | Invitations, role allowlist, last-owner protection, `switch-company` active membership validation. |
| RBAC & tenant isolation (auth/company) | **COMPLETE** | `@PreAuthorize` + service checks in `CompanyController` / `CompanyAdministrationService`. |
| CSRF & rate limiting | **COMPLETE** | `CookieCsrfTokenRepository.withHttpOnlyFalse()`, `AuthRateLimitFilter` (30 req/min/IP+path). |
| Phase 2.2 regression suite | **UNVERIFIED** | Currently blocked from running by the V4 entity/flyway-target mismatch; must re-verify on PostgreSQL 17. |

### Phase 2.3 — Company Rights and Licence Registry
| Requirement | Classification | Evidence & Notes |
|---|---|---|
| Schema enhancements (V4) | **PARTIAL** | `V4__rights_and_licence_registry_enhancements.sql` drafted but unapplied; `created_by_membership_id` / `uploaded_by_membership_id` use single-column FKs instead of same-company composite FKs `(company_id, *_membership_id)`. |
| RightsAsset / LicenceRecord / LicenceDocument models | **PARTIAL** | Entities partially updated; `LicenceDocument` incomplete; `RightsAsset.sha256Checksum` missing `SqlTypes.CHAR`; `LicenceRecord` lifecycle rules not yet implemented. |
| Storage abstraction (`StorageService`, `LocalStorageService`, `StorageProperties`, `StoredFile`) | **PARTIAL** | `StorageService`, `LocalStorageService`, `StorageException`, `StoredFile` exist; `StorageProperties` missing (`ServiceProperties.Storage` used instead); lacks file signature/MIME/empty-file checks, multipart size config, atomic temp write, and orphan cleanup/Docker volume mount. |
| Rights Registry service layer & lifecycle | **MISSING** | No service class exists for assets, licences, associations, document upload/download, lifecycle computation, or dashboard. |
| Rights Registry controllers & APIs | **MISSING** | Only `RegistryDtos.java` exists; no controllers for `/api/v1/rights-assets`, `/api/v1/licences`, or `/api/v1/rights-registry/dashboard`. |
| Registry audit integration | **MISSING** | Audit event types (`RIGHTS_ASSET_*`, `LICENCE_*`, `LICENCE_DOCUMENT_*`) not yet emitted. |
| Registry frontend (10 required views/flows) | **MISSING** | `/rights-registry` renders `<PlaceholderPage title="Rights Registry"/>`; `DashboardPage` shows `"Not available"` for active licences; `api.ts` cannot send `FormData` without overriding `Content-Type`. |
| Docker persistent storage & config alignment | **PARTIAL** | `docker-compose.yml` lacks persistent volume for `mediacheck-data` on `backend`; `.env` has `B2_CONFIGURED=true` and `SERVER_PORT=8082` vs Compose `8080`. |
| Phase 2.3 automated & E2E tests | **MISSING** | No registry integration, tenant-isolation, file-integrity, or E2E browser tests exist. |

### Additional Phase 2 Scope Check
Inspection of `docs/phase-0-requirements-freeze.md`, `docs/architecture.md`, `docs/phase-2.1-database-design.md`, `docs/phase-2.2-authentication-security.md`, and `docs/phase-2.3-inspection.md` confirms there is **no Phase 2.4** in the approved Phase 2 plan. Policies, media scanning, AI workers, and reporting belong to Phase 3+ and will **not** be started.

---

## 4. Immediate Remediation Plan

1. **Stage 1 (Phase 2.1 Validation):** Inspect and refine `V4` migration (composite tenant FKs, constraints, indexes) before applying it; fix `RightsAsset`, `LicenceRecord`, `LicenceDocument`, and `RecordStatus` mappings; validate V1–V4 against real PostgreSQL 17.
2. **Stage 2 (Phase 2.2 Regression):** Re-run the full Phase 2.2 authentication, RBAC, CSRF, rate-limit, and Mailpit tests against PostgreSQL 17 to confirm zero regression.
3. **Stage 3 (Phase 2.3 Storage & Infrastructure):** Add `StorageProperties`, harden `LocalStorageService` (MIME/extension/signature validation, atomic write, path traversal, orphan cleanup), configure multipart limits, and mount persistent storage volume in Docker Compose.
4. **Stage 4 (Phase 2.3 Backend):** Implement `RightsRegistryService`, `RightsAssetController`, `LicenceController`, `RightsRegistryDashboardController`, RBAC enforcement (`OWNER`/`ADMIN` write, `REVIEWER`/`MEMBER` read), lifecycle rules, and audit events.
5. **Stage 5 (Phase 2.3 Frontend):** Fix `api.ts` ESLint + `FormData`/blob download support; implement all 10 Rights Registry views/workflows and connect `DashboardPage` active-licences metric to real backend APIs.
6. **Stages 6–8 (Integration, Security, Acceptance & Docs):** Rebuild/verify Docker services, execute two-company isolation + SHA-256 + PostgreSQL 17 + browser E2E acceptance, and deliver all required `docs/` reports.

