# Architecture decision record: image-review V1

## Status

Accepted for Phase 1 implementation.

## Decision

MEDIACHECK V1 supports secure review of `JPEG`, `PNG`, and `WebP` images only. It uses React and TypeScript for the user interface, Spring Boot as the authoritative API and policy engine, PostgreSQL for structured and auditable state, self-hosted RabbitMQ for asynchronous scan work, Backblaze B2-compatible private object storage, and a Python/FastAPI analysis service.

```text
React UI -> Spring Boot API -> PostgreSQL
                   |             |
                   |             +-> immutable audit, policy, evidence, decision records
                   +-> private object storage (original media and rights evidence)
                   +-> RabbitMQ -> Python image worker -> normalized evidence
                                                    |
                                                    +-> Spring Boot deterministic policy engine
                                                               -> official decision and report
```

Spring Boot is the sole authority for tenancy, rights records, licence scope, policy evaluation, decisions, reports, and audit history. Python may return evidence only. A report-generation model, when introduced, may explain an official decision but may never alter it.

## Consequences

- Browser requests never establish their own company identity; the API derives it from authentication.
- A new rights submission begins as `USER_DECLARED`; it is not verified permission.
- Rights assets and rights/permission records are separate. New terms create a linked version rather than changing historical terms.
- The Phase 1 storage seam defaults to local filesystem configuration. Backblaze B2 uses its S3-compatible API in a later integration phase; bucket endpoint and region are supplied from actual bucket configuration, not guessed.
- The first milestone implements image rights registration and verification end-to-end. Other asset categories remain contract-ready but unimplemented.
- New media types must add adapters and analysis modules while reusing the evidence, policy, reporting, and audit boundaries.
