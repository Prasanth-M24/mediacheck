# Phase 2.3 Baseline Repository Inspection Report

**Date:** 2026-11-01  
**Author:** MEDIACHECK Architecture Team  
**Scope:** Stage 0 Codebase, Database Schema, and Security Model Inspection for Phase 2.3 Company Rights & Licence Registry.

---

## 1. Existing Relevant Entities and Domain Model

Inspection of `backend/src/main/java/com/mediacheck/domain/`:

### A. Core Foundation Classes
- **`BaseEntity`**: Defines `UUID id` (`@GeneratedValue(strategy = GenerationType.UUID)`).
- **`TenantOwnedEntity`**: Extends `BaseEntity`; defines immutable `Company company` (`@ManyToOne(fetch = FetchType.LAZY, optional = false)`, `company_id` column). Every tenant-scoped entity inherits from this class.

### B. Phase 2.1 Pre-existing Entities
1. **`RightsAsset`** (`@Table(name = "rights_assets")`):
   - `title`: `VARCHAR(300) NOT NULL`
   - `assetType`: `VARCHAR(64) NOT NULL` (e.g. IMAGE, LOGO, VIDEO, AUDIO, FONT, BRAND_ASSET)
   - `externalReference`: `VARCHAR(200)`
   - `rightsHolder`: `VARCHAR(300)`
   - `status`: `RecordStatus` (`ACTIVE`, `ARCHIVED`)
   - `company`: Inherited from `TenantOwnedEntity`
2. **`LicenceRecord`** (`@Table(name = "licence_records")`):
   - `licenceReference`: `VARCHAR(200) NOT NULL`
   - `licensorName`: `VARCHAR(300) NOT NULL`
   - `licenseeName`: `VARCHAR(300)`
   - `validFrom`: `TIMESTAMPTZ` (`Instant`)
   - `validUntil`: `TIMESTAMPTZ` (`Instant`)
   - `status`: `RecordStatus` (`DRAFT`, `ACTIVE`, `EXPIRED`, `REVOKED`)
   - `termsJson`: `JSONB` (`@JdbcTypeCode(SqlTypes.JSON)`)
   - `rightsAssets`: `@ManyToMany` joined via `licence_rights_assets`
   - `company`: Inherited from `TenantOwnedEntity`
3. **`LicenceDocument`** (`@Table(name = "licence_documents")`):
   - `licenceRecord`: `@ManyToOne(fetch = FetchType.LAZY, optional = false)`
   - `storageKey`: `VARCHAR(512) NOT NULL`
   - `originalFilename`: `VARCHAR(512) NOT NULL`
   - `mimeType`: `VARCHAR(127) NOT NULL`
   - `sha256Checksum`: `CHAR(64)`
   - `company`: Inherited from `TenantOwnedEntity`

### C. Relationship Entities
- **`licence_rights_assets`**: Join table with composite foreign keys enforcing company-level isolation:
  - `(company_id, licence_record_id) -> licence_records(company_id, id)`
  - `(company_id, rights_asset_id) -> rights_assets(company_id, id)`
  - `PRIMARY KEY (licence_record_id, rights_asset_id)`
  - `UNIQUE (company_id, licence_record_id, rights_asset_id)`

---

## 2. Existing Database Schema and Migrations

Inspection of `backend/src/main/resources/db/migration/`:
- **`V1__create_core_domain_schema.sql`**: Created core domain schema, including `companies`, `users`, `company_memberships`, `rights_assets`, `licence_records`, `licence_rights_assets`, `licence_documents`, and `audit_events`.
- **`V2__authentication_and_membership_security.sql`**: Added password hash, account lockout, refresh sessions, email verification tokens.
- **`V3__authentication_uniqueness_indexes.sql`**: Added lower-case functional uniqueness indexes on emails.
- **Next Migration Version**: **`V4__rights_and_licence_registry_enhancements.sql`** will be created to add supplementary metadata columns required by Phase 2.3 without breaking existing V1 constraints.

---

## 3. Membership, Permissions, and Authorization Model

### Roles (`MembershipRole`)
- `OWNER`: Full company control. Can register, edit, archive, revoke assets, licences, documents, and manage company settings.
- `ADMIN`: Full registry management permissions (create, edit, archive, link assets, upload documents).
- `REVIEWER`: Read-only registry visibility for rights evaluation. Can view assets, licences, documents, and audit logs.
- `MEMBER`: Basic read-only access within the active company.

### Authentication Context
- Validated through Spring Security's `BearerTokenAuthenticationToken` parsed via Nimbus JWT.
- In `SecurityConfiguration`, `liveMembershipAuthentication` confirms the user is active, email verified, membership active, company active, refresh session valid, and role unmodified.
- Controllers resolve caller context securely via:
  - `AuthController.companyId(jwt)` -> Company UUID
  - `AuthController.userId(jwt)` -> PlatformUser UUID
  - `AuthController.membershipId(jwt)` -> CompanyMembership UUID
  - `@PreAuthorize("hasAnyRole('OWNER','ADMIN','REVIEWER','MEMBER')")`

---

## 4. Audit Architecture

- Entity: `AuditEvent` (`audit_events` table).
- Service pattern (`CompanyAdministrationService.java` & `AuthService.java`):
  ```java
  AuditEvent event = new AuditEvent(company, eventType, subjectType, subjectId, metadataJson, Instant.now());
  event.setActor(actorUserId, actorMembershipId);
  auditEventRepository.save(event);
  ```
- Phase 2.3 Audit Types:
  - `RIGHTS_ASSET_CREATED`, `RIGHTS_ASSET_UPDATED`, `RIGHTS_ASSET_ARCHIVED`
  - `LICENCE_CREATED`, `LICENCE_UPDATED`, `LICENCE_REVOKED`
  - `LICENCE_ASSET_LINKED`, `LICENCE_ASSET_UNLINKED`
  - `LICENCE_DOCUMENT_UPLOADED`, `LICENCE_DOCUMENT_DOWNLOADED`

---

## 5. Frontend Architecture & API Conventions

- Framework: React 19, Vite 6, Tailwind CSS 4, React Router 7.
- Shell: `App.tsx` renders top navigation with company switcher, account menu, and route outlet.
- UI Components in `components/ui.tsx`:
  - `PageHeader`, `SectionCard`, `MetricCard`, `LoadingState`, `ErrorState`, `EmptyState`, `Badge`.
- API Client in `services/api.ts`:
  - `apiRequest<T>(path, init)` automatically manages CSRF tokens (`X-XSRF-TOKEN`), Bearer token header, dynamic host routing (`127.0.0.1` vs `localhost`), and 401 token refresh retry.
- Route: `/rights-registry` currently renders `<PlaceholderPage title="Rights Registry"/>`.

---

## 6. Implementation Gaps for Phase 2.3

1. **Storage Subsystem**: No file storage service currently exists in the backend. Need a clean `StorageService` interface with a `LocalStorageService` implementation that stores files safely under `mediacheck-data/licence-documents/<company-id>/<document-id>.<ext>`.
2. **Repositories**:
   - `RightsAssetRepository`
   - `LicenceRecordRepository`
   - `LicenceDocumentRepository`
3. **Database Schema Additions (Flyway V4)**:
   - Add `description`, `original_filename`, `mime_type`, `file_size`, `sha256_checksum`, `created_by_membership_id` to `rights_assets`.
   - Add `name`, `description`, `licence_type`, `created_by_membership_id` to `licence_records`.
   - Add `file_size` to `licence_documents`.
4. **Service Layer**:
   - `RightsRegistryService` managing assets, licences, document uploads/downloads, associations, and audit logging.
5. **Controllers**:
   - `RightsAssetController` (`/api/v1/rights-assets`)
   - `LicenceController` (`/api/v1/licences`)
   - `RightsRegistryDashboardController` (`/api/v1/rights-registry/dashboard`)
6. **Frontend Interface**:
   - Full Rights Registry module with Dashboard, Assets List, Asset Details, Licences List, Licence Details, Creation/Edit dialogs, Document Upload & Download.

---

## 7. Potential Compatibility & Security Risks

1. **Cross-Tenant IDOR**: Every query and mutation MUST be scoped by `company_id` from the JWT token. Never allow client payload `companyId` injection.
2. **Cross-Tenant File Access**: Document download must verify company ownership before streaming file bytes.
3. **Pessimistic Integrity**: Use composite foreign keys (`company_id, licence_record_id`) so PostgreSQL strictly prohibits linking assets belonging to a different company.
4. **Path Traversal Defense**: Storage keys must be generated as internal UUID-based paths, never trusting user-provided original filenames.
