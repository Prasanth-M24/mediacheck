# Phase 2.1 final verification and database audit

This report records the audit performed on 2026-10-31. No migration, contract, `.env`, or database data was changed by these checks.

## Schema inventory

The prompt names 18 domain tables. The migration contains those 18 plus one supporting table, `licence_rights_assets`, which models the requested many-to-many case where one licence covers one or more registered rights assets. It is a join table, bringing the physical total to 19.

Actual tables:

`companies`, `users`, `company_memberships`, `rights_assets`, `licence_records`, `licence_rights_assets` (supporting join table), `licence_documents`, `company_policies`, `policy_versions`, `media_assets`, `scan_jobs`, `pipeline_runs`, `check_results`, `findings`, `evidence_records`, `decisions`, `reports`, `review_actions`, `audit_events`.

Company ownership columns appear on 17 of 19 tables. `companies` is the tenant root and `users` is a global identity table; all other tables carry `company_id`.

### Foreign keys

There are 17 explicitly declared table-level foreign keys and 18 inline `REFERENCES` clauses, for 35 foreign keys total:

| Source | Target | Delete behavior |
| --- | --- | --- |
| `company_memberships.company_id` | `companies.id` | RESTRICT |
| `company_memberships.user_id` | `users.id` | RESTRICT |
| `rights_assets.company_id` | `companies.id` | RESTRICT |
| `licence_records.company_id` | `companies.id` | RESTRICT |
| `licence_rights_assets.company_id` | `companies.id` | RESTRICT |
| `licence_rights_assets(company_id, licence_record_id)` | `licence_records(company_id, id)` | CASCADE |
| `licence_rights_assets(company_id, rights_asset_id)` | `rights_assets(company_id, id)` | RESTRICT |
| `licence_documents.company_id` | `companies.id` | RESTRICT |
| `licence_documents(company_id, licence_record_id)` | `licence_records(company_id, id)` | CASCADE |
| `company_policies.company_id` | `companies.id` | RESTRICT |
| `policy_versions.company_id` | `companies.id` | RESTRICT |
| `policy_versions(company_id, policy_id)` | `company_policies(company_id, id)` | RESTRICT |
| `media_assets.company_id` | `companies.id` | RESTRICT |
| `media_assets(company_id, uploaded_by_user_id, uploaded_by_membership_id)` | `company_memberships(company_id, user_id, id)` | RESTRICT |
| `scan_jobs.company_id` | `companies.id` | RESTRICT |
| `scan_jobs(company_id, media_asset_id)` | `media_assets(company_id, id)` | RESTRICT |
| `pipeline_runs.company_id` | `companies.id` | RESTRICT |
| `pipeline_runs(company_id, scan_job_id)` | `scan_jobs(company_id, id)` | CASCADE |
| `check_results.company_id` | `companies.id` | RESTRICT |
| `check_results(company_id, pipeline_run_id)` | `pipeline_runs(company_id, id)` | CASCADE |
| `findings.company_id` | `companies.id` | RESTRICT |
| `findings(company_id, pipeline_run_id)` | `pipeline_runs(company_id, id)` | CASCADE |
| `evidence_records.company_id` | `companies.id` | RESTRICT |
| `evidence_records(company_id, pipeline_run_id)` | `pipeline_runs(company_id, id)` | CASCADE |
| `evidence_records(company_id, finding_id)` | `findings(company_id, id)` | RESTRICT |
| `decisions.company_id` | `companies.id` | RESTRICT |
| `decisions(company_id, scan_job_id)` | `scan_jobs(company_id, id)` | RESTRICT |
| `decisions(company_id, policy_version_id)` | `policy_versions(company_id, id)` | RESTRICT |
| `reports.company_id` | `companies.id` | RESTRICT |
| `reports(company_id, decision_id)` | `decisions(company_id, id)` | RESTRICT |
| `review_actions.company_id` | `companies.id` | RESTRICT |
| `review_actions(company_id, scan_job_id)` | `scan_jobs(company_id, id)` | RESTRICT |
| `review_actions(company_id, actor_user_id, actor_membership_id)` | `company_memberships(company_id, user_id, id)` | RESTRICT |
| `audit_events.company_id` | `companies.id` | RESTRICT |
| `audit_events(company_id, actor_user_id, actor_membership_id)` | `company_memberships(company_id, user_id, id)` | RESTRICT |

The `(company_id, referenced_id)` constraints prevent cross-company references for the modeled tenant-to-tenant associations. The three-part actor constraints tie an actor user and membership to the same company. Nullable audit actor fields are paired by a CHECK constraint. `licence_rights_assets` applies the same-company key to both sides. The schema does not add composite FKs for global `users` or `companies`, which are roots rather than tenant-owned children.

The entity mappings do not express every composite FK as an association. `MediaAsset` and actor records retain membership IDs as scalar fields; PostgreSQL remains the enforcement layer for those composite relationships.

### Unique constraints and indexes

Unique constraints in the migration:

- `companies.slug`; `users.email`.
- `company_memberships(company_id, user_id)` and `(company_id, user_id, id)`.
- `rights_assets(company_id, id)` and `(company_id, external_reference)`.
- `licence_records(company_id, licence_reference)` and `(company_id, id)`.
- `licence_rights_assets(licence_record_id, rights_asset_id)` and `(company_id, licence_record_id, rights_asset_id)`.
- `licence_documents(company_id, storage_key)`.
- `company_policies(company_id, id)` and `(company_id, name)`.
- `policy_versions(company_id, id)` and `(policy_id, version_number)`.
- `media_assets(company_id, storage_key)`, `(company_id, id)`, `(company_id, sha256_checksum)`.
- `scan_jobs(company_id, id)`; `pipeline_runs(company_id, id)`.
- `check_results(company_id, id)` and `(pipeline_run_id, check_name)`.
- `findings(company_id, id)` and `(pipeline_run_id, external_reference)`.
- `evidence_records(company_id, storage_key)`.
- `decisions(company_id, id)` and `scan_job_id`.
- `reports(decision_id)`.

There are 14 additional named indexes: `idx_memberships_user_company`, `idx_rights_assets_company_status`, `idx_licence_records_company_status`, `idx_licence_documents_licence`, `idx_policy_versions_company_policy`, `idx_media_assets_company_uploaded`, `idx_scan_jobs_company_status_created`, `idx_pipeline_runs_scan_job`, `idx_check_results_pipeline_run`, `idx_findings_pipeline_run`, `idx_evidence_pipeline_run`, `idx_decisions_company_decided`, `idx_review_actions_company_scan`, and `idx_audit_events_company_occurred`. PostgreSQL also creates backing indexes for primary keys and unique constraints.

Deletion is generally restrictive to retain business and audit history. Pipeline child rows cascade with their run; licence documents and licence-to-right associations cascade with their licence. Evidence-to-finding references are restrictive rather than nullable-on-delete. The migration is additive and has no drop, truncate, or data reset statement.

## Runtime verification

| Area | Command or inspection | Result | Status |
| --- | --- | --- | --- |
| Maven wrapper | `backend/.mvn/wrapper/maven-wrapper.properties`, `.\mvnw.cmd -version` | Wrapper is present, script-only distribution points to Maven 3.9.12; wrapper command initially hit a PowerShell `icm`/null-array startup error in the restricted run. Elevated invocation later launched Maven successfully. | PASS (after elevated invocation) |
| Java/Maven | `java -version`, `mvn -version` | Java 22.0.2; Maven 3.9.12. Project compiler targets Java 21. | PASS (tool inventory) |
| Maven settings/cache | Inspected settings presence/counts and parent POM cache path without printing private values | No user `settings.xml` found; no proxy environment variables present; cached Spring Boot parent POM exists. | PASS (diagnostic) |
| Maven test | `.\mvnw.cmd test` with approved network access | Dependency resolution and compilation succeeded, then tests failed: `DomainPersistenceTest` context could not initialize because H2 has no `audit_events` table while Hibernate uses `ddl-auto=validate`. Existing system-info test passed. Overall: 4 tests, 2 context errors, build failure. | FAIL |
| Maven package | `.\mvnw.cmd package -DskipTests` with approved network access | Recompiled/checks and Spring Boot repackaging completed successfully. Tests were explicitly skipped. | PASS |
| Compose PostgreSQL | `docker version`, `docker info`, `docker compose ... ps -a` | Docker CLI 29.9.2 / Compose 5.6.1 present; engine named pipe unavailable, so server info and Compose container list cannot be read. No existing Compose container was verified. | BLOCKED |
| Local PostgreSQL | `Get-Service`, `pg_isready`, `psql --version`, read-only query against configured database | Windows service `postgresql-x64-18` is running; local client is 18.6; port 5432 accepts connections. Configured database `mediacheck` does not exist. Connection to maintenance database returned server version `PostgreSQL 18.6`; its non-template database list contained only `postgres`. Port 5433 did not respond. | PASS (local server identified; not target version) |
| PostgreSQL 17 migration | No migration was run | Compose PostgreSQL 17 is unavailable; configured local service is PostgreSQL 18.6 and configured `mediacheck` database is absent. Did not create a database or apply a migration to a different target. | BLOCKED |
| Hibernate schema validation | Attempted indirectly by JPA test against H2 | Failed before any PostgreSQL validation; missing H2 table `audit_events`. No claim of PostgreSQL Hibernate validation. | BLOCKED |
| Repository persistence | `DomainPersistenceTest` via Maven test | Not exercised because its Spring context failed during schema validation. | BLOCKED |
| Contracts | `ConvertFrom-Json` and SHA-256 inventory for four `contracts/*.json` files | All four parse. Current hashes are listed below, but no Git history/baseline exists to prove byte-for-byte historical preservation. Contract files were not modified during this audit. | PASS (syntax/current inventory); historical comparison unavailable |
| Git history | `git rev-parse --is-inside-work-tree`, `git status --short` | Workspace is not a Git repository. | NOT AVAILABLE |
| Data preservation | Read-only database checks; Compose/DB migration not run | No database writes, volume deletion, or reset were performed. | PASS |

Current contract SHA-256 values:

| Contract file | SHA-256 |
| --- | --- |
| `example-evidence-bundle.json` | `5915026B6515614AB4DC7BC9457576133B072ACDE6C7EC6AADD7DF372272AB74` |
| `mediacheck-evidence-bundle.schema.json` | `B3F7FC0ADA217CADBAB1A18B286473398A830A15BE69088B035171A8E07DBD39` |
| `mediacheck-policy-decision.schema.json` | `A5C8A7C3B290779F40B3136AEE2BD697E755A0E8F62707EB0D2E31682419CF1D` |
| `mediacheck-reasoning-report.schema.json` | `0800DD481C959332830DF3343145561602D5D0D892F9274641B218A87B3640EC` |

## Tenant isolation audit

The seven repository interfaces extend unrestricted `JpaRepository`, which exposes generic `findAll`, `findById`, and mutation methods without a company predicate:

| Repository | Risk |
| --- | --- |
| `CompanyMembershipRepository` | Unrestricted access to membership records; company-scoped derived queries also exist. |
| `MediaAssetRepository` | Unrestricted access to tenant-owned media metadata; company-scoped queries also exist. |
| `ScanJobRepository` | Unrestricted access to tenant-owned jobs; company-scoped queries also exist. |
| `DecisionRepository` | Unrestricted access to tenant-owned decisions; company-scoped queries also exist. |
| `AuditEventRepository` | Unrestricted access to tenant audit history; only a company-scoped list method is added. |
| `CompanyRepository` | Global company lookup is unrestricted; this is a tenant-root table, but must be protected in future service/API layers. |
| `PlatformUserRepository` | Global identity lookup is unrestricted by design; must only be reachable through trusted identity services. |

Thus, company-aware FKs protect relational consistency, and some repository methods require `companyId`; neither provides complete application tenant isolation. There is no authentication, authorization, active-company derivation, tenant-scoped service boundary, or database row-level security. Never accept a frontend-supplied company ID as authority.

## Phase 2.1.2 remediation (2026-10-31)

The earlier audit above is retained as historical evidence. This section records the follow-up fixes and verification.

### Root cause and changes

- The test profile set `spring.flyway.enabled: false`; `@DataJpaTest` therefore started Hibernate `ddl-auto: validate` against a blank in-memory database. The reported missing `audit_events` table was not evidence that the migration omitted that table.
- Flyway is now enabled in `application-test.yml`. The persistence slice now uses the configured database (`@AutoConfigureTestDatabase(replace = NONE)`) instead of silently replacing it with another H2 database.
- H2 2.3 does not recognize PostgreSQL's `TIMESTAMPTZ` alias even in PostgreSQL mode. The test-only JDBC URL defines `TIMESTAMPTZ` as `TIMESTAMP WITH TIME ZONE`; this does not add or replace any table. The production migration remains PostgreSQL SQL.
- Running the real migration exposed two missing unique targets required by composite foreign keys: `company_memberships(company_id, user_id, id)` for same-company actor references, and `policy_versions(company_id, id)` for decisions. These keys were added to V1. The configured `mediacheck` database had already been confirmed absent and no local migration history existed, so no existing local schema/data was changed. Any other environment that may have applied V1 must be checked before deploying this amended V1 because Flyway checksums will differ.
- `BaseEntity` now maps only the UUID primary key. It no longer imposes `created_at` and `updated_at` on tables whose migration does not contain those columns. `ScanJob` explicitly maps `created_at` because its repository's derived ordering query depends on that property. SHA-256/checksum fields now declare `CHAR(64)` to match the migration.
- Persistence coverage now checks the migrated table/FK inventory, inserts the rights/licence/policy/media/scan/pipeline/findings/evidence/decision/report/review graph, exercises scan status updates, verifies required-field and duplicate-membership rejection, checks cross-company media/policy/audit-actor reference rejection, and persists then reloads an `AuditEvent` through JPA.

### Follow-up results

| Area | Command or inspection | Result | Status |
| --- | --- | --- | --- |
| Docker / Compose PostgreSQL 17 | `docker version`, `docker info` | Docker CLI is present, but the engine named pipe remains unavailable. PostgreSQL 17 Compose container cannot be started or inspected. | BLOCKED |
| Test database | Full Spring JPA test slice | Isolated in-memory H2 2.3.232. Flyway V1 applied successfully; Hibernate schema validation initialized all entities. Flyway warns its latest tested H2 version is 2.2.224, so this is not PostgreSQL-equivalent verification. | PASS (H2 only) |
| Migration inventory | Persistence test against migrated H2 schema | 19 expected tables and 35 foreign keys found; Flyway history records V1 success. | PASS (H2 only) |
| Persistence / constraints | `DomainPersistenceTest` | Company/user/membership repository persistence, workflow table inserts, scan lifecycle update, duplicate membership uniqueness, cross-company media/policy FK rejection, and JPA audit event save/reload passed. | PASS (H2 only) |
| Full backend tests | Maven Wrapper `test` goal | 8 tests, 0 failures, 0 errors, 0 skipped. | PASS |
| Package | Maven Wrapper `package -DskipTests` goal | Spring Boot JAR packaging succeeded; tests explicitly skipped for this goal. | PASS |
| PostgreSQL 17 migration and Hibernate validation | Not run | Docker unavailable. Local PostgreSQL is 18.6 and is not accepted as proof of PostgreSQL 17. No migration was run against it. | BLOCKED |

H2 compatibility is limited to exercising the migration syntax paths accepted by H2, JPA mappings, basic persistence, and tested constraints. PostgreSQL-specific semantics, exact PostgreSQL 17 foreign-key/index metadata, JSONB behavior, and the production driver remain unverified. PostgreSQL 17 remains the acceptance database.

### Current verdict

**PARTIALLY COMPLETE** - test-profile Flyway ordering is fixed, H2 migration and Hibernate validation pass, the persistence suite passes, and packaging passes. PostgreSQL 17 migration/runtime verification remains blocked by Docker engine unavailability. No Phase 2.2 work was started.

## Phase 2.1 final PostgreSQL 17 acceptance (2026-10-31)

This section supersedes the PostgreSQL-blocked statements above for the final acceptance run. The earlier audit remains as historical evidence.

| Check | Actual result | Status |
| --- | --- | --- |
| Compose/Docker | Compose defines `postgres:17-alpine`, with persistent volume `infrastructure_postgres-data`. Docker Engine 29.9.2 was available. The existing service was already running and healthy; it was not recreated. | PASS |
| Server/database | `mediacheck` connected on host port 5433; server reported PostgreSQL 17.11. The preserved volume was mounted at `/var/lib/postgresql/data`. | PASS |
| Pre-migration history | `flyway_schema_history` existed but had zero rows. `mediacheck` had no domain tables or application rows. There was no earlier successful V1 and no stored checksum to conflict with the current file. | PASS |
| Flyway | Spring Boot validated the current V1 checksum and applied `V1__create_core_domain_schema.sql` once. Subsequent startup and tests validated V1 with no checksum mismatch and no further migration. | PASS |
| PostgreSQL schema | 19 domain tables plus Flyway history; 35 foreign keys; 75 unique constraints; 60 domain indexes (62 including Flyway history indexes). The 35 FKs include company-aware composite references for licences, media membership, scan/pipeline/evidence workflow, policy decisions, reports, review actions, and audit actors. | PASS |
| Hibernate/runtime | Spring Boot started against PostgreSQL 17.11 with `ddl-auto: validate`; JPA `EntityManagerFactory` initialized. `/actuator/health` returned `UP`. | PASS |
| Persistence tests | Maven ran all 8 tests against PostgreSQL 17.11: 0 failures, 0 errors, 0 skipped. This included workflow persistence, `AuditEvent` JPA save/reload, duplicate membership rejection, cross-company FK rejection, and company-aware membership relationships. Sample test data rolled back; final company and audit-event row counts were both zero. | PASS |
| Maven package | `mvnw.cmd package -DskipTests` built and repackaged the backend JAR successfully. Tests are intentionally skipped by this command. | PASS |
| Data/volume preservation | No `down -v`, volume reset, or deletion occurred. The existing named volume remained mounted and healthy. | PASS |

PostgreSQL domain tables observed: `companies`, `users`, `company_memberships`, `rights_assets`, `licence_records`, `licence_rights_assets`, `licence_documents`, `company_policies`, `policy_versions`, `media_assets`, `scan_jobs`, `pipeline_runs`, `check_results`, `findings`, `evidence_records`, `decisions`, `reports`, `review_actions`, and `audit_events`. `flyway_schema_history` is the additional Flyway support table.

### Acceptance fixes made

- V1 was not changed. The database had no successful migration row, so no checksum rewrite or forward migration was needed.
- Hibernate's PostgreSQL validation required explicit `CHAR` JDBC mappings for the three `CHAR(64)` checksum fields; those entity mappings now use `SqlTypes.CHAR`.
- Persistence tests now cast JSON parameters to `jsonb` and use savepoints around expected constraint violations, matching PostgreSQL transaction behavior.

### Final verdict

**COMPLETE** — PostgreSQL 17 migration, Flyway checksum validation, Hibernate schema validation, schema inventory, representative persistence and relationships, Maven tests, and package all passed. No Phase 2.2 work was started. Files changed in this acceptance run: `EvidenceRecord.java`, `LicenceDocument.java`, `MediaAsset.java`, `DomainPersistenceTest.java`, and this verification document.

## Historical verdict (pre-Phase 2.1.2)

**PARTIALLY COMPLETE** — implementation is present and the package goal passes, but `mvn test` fails, PostgreSQL 17 migration and schema validation were not run, and tenant isolation is not complete. Do not begin Phase 2.2 until these items are reviewed and resolved.
