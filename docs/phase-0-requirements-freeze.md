# Phase 0: requirements freeze

## Inputs reviewed

- Master MEDIACHECK implementation specification supplied as pasted text.
- `MEDIACHECK_Final_Rights_Registration_Form_Specification.docx`.
- The shared ChatGPT URL was provided for detail, but its public content could not be fetched in this environment. No requirements were inferred from it.

## Repository assessment

The workspace was empty: no source code, contracts, infrastructure, Git metadata, or service configuration existed. The clean layout in the repository root is therefore the starting structure.

## Finalized V1 scope

Included: company authentication; tenant isolation; image-only upload and scanning for JPEG, PNG, and WebP; image rights registration; private evidence storage; asynchronous processing; provenance, rights, and deployment-readiness evidence; deterministic `APPROVE`, `REVIEW`, and `BLOCK` decisions; human review; audit history; reports.

Excluded: production support for audio, video, animated images, PDFs, text/caption workflows, unrestricted policy scripting, and any claim that automation establishes legal compliance or infringement.

The rights-registration UI will initially enable the `IMAGE` category. Its data model preserves the supplied universal categories and conditional form behaviour for later workflows.

## Component boundaries

| Component | Owns | Does not own |
| --- | --- | --- |
| React frontend | User interaction, client validation, status display | Authorization, official decisions, credentials |
| Spring Boot | Auth, tenant isolation, rights registry, policy, jobs, decisions, audit | ML analysis authority |
| Python worker | Image decoding and normalized analysis evidence | Licence authority or final policy outcome |
| PostgreSQL | Transactional records and audit trail | Raw private files |
| Object storage | Original media and supporting evidence objects | Public unauthenticated access |
| RabbitMQ | Durable background-job transport | Business-state authority |

## Initial domain model

`companies` 1--* `company_memberships` *--1 `users` establishes tenant membership and roles.

`companies` 1--* `rights_assets` 1--* `licence_records` 1--* `licence_documents`. A rights asset identifies a work; each licence/permission/ownership claim contains a time-bounded scope and immutable version history.

`companies` 1--* `company_policies` 1--* `policy_versions`. Every decision references exactly one policy version.

`companies` 1--* `media_assets` 1--* `scan_jobs` 1--* `pipeline_runs` 1--* `check_results` / `findings` / `evidence_records`. A completed scan has one official `decision`, zero or one `report`, and zero or more `review_actions` and `audit_events`.

All company-owned tables carry a company foreign key, are queried with a server-derived tenant predicate, and are covered by cross-tenant authorization tests.

## Shared-contract validation

The supplied contract files were absent, so version `1.0` schemas were created under `contracts/`.

- Evidence bundles require scan and company identifiers, pipeline execution records, and structured findings.
- Policy decisions require the same scan/company identifiers, a policy version, final decision, fired rule IDs, and evidence references.
- Reasoning reports require the official decision and must repeat, rather than replace, it.
- The example evidence bundle validates structurally against the evidence schema. Full automated schema validation will be introduced with the first service test suite.

## Phased checklist

1. Phase 1: initialize the three services, local PostgreSQL/RabbitMQ Compose configuration, health endpoints, and setup guide.
2. Phase 2: Flyway schema, API contracts, seeded test data, and contract tests.
3. Phase 3: registration, login, roles, JWT, and tenant-isolation tests.
4. Phase 4: image rights assets, rights terms, document metadata, and verification queue.
5. Phase 5: versioned policy configuration.
6. Phase 6: secure upload, private object storage integration, and idempotent jobs.
7. Phases 7–11: image adapters, analysis pipelines, and evidence aggregation.
8. Phase 12: deterministic policy engine and auditability.
9. Phases 13–14: grounded reports and complete frontend workflow.
10. Phases 15–16: security, evaluation, deployment preparation, and documentation.

## Phase 1 prerequisites and commands

Install JDK 21, Node.js 22 LTS, Python 3.12, Docker Desktop, and Docker Compose. Backblaze B2 is the intended private object-storage provider; use local filesystem configuration until B2 integration begins. Copy `.env.example` to `.env` and change local development passwords.

Planned commands after Phase 1 scaffolding exists:

```powershell
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d
cd backend; ./mvnw spring-boot:run
cd ../ai-service; .\.venv\Scripts\Activate.ps1; uvicorn app.main:app --reload
cd ../frontend; npm run dev
```

## Phase 0 acceptance result

Architecture, scope, responsibilities, initial entities, and contracts are documented. No application code has been prematurely introduced.
