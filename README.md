# MEDIACHECK

MEDIACHECK is an image-first, multi-tenant platform for AI content and rights review. Phase 3 adds authenticated image ingestion, private storage, asynchronous review jobs, evidence aggregation, deterministic decisions, reports, and audit history.

## Repository layout

```text
frontend/        React, TypeScript, Vite, Tailwind application
backend/         Spring Boot authentication, registry, media-review and report APIs
ai-service/      FastAPI private analysis-service boundary
infrastructure/  Docker Compose for local services
contracts/       Versioned JSON Schemas shared by services
docs/            Architecture and setup documentation
```

## Run locally

Copy `.env.example` to `.env`, then run `docker compose --env-file .env -f infrastructure/docker-compose.yml up --build`.

See [development setup](docs/development-setup.md), [architecture](docs/architecture.md), and [the Phase 0 report](docs/phase-0-requirements-freeze.md) for details.

The Phase 2.1 database schema, tenancy model, and verification status are documented in [the database design](docs/phase-2.1-database-design.md).

## Current limitations

Phase 3 is documented as partially complete in [the Phase 3 report](docs/phase-3-completion.md). External AI reasoning, validated AI-generation detection, perceptual rights matching, and PostgreSQL/RabbitMQ/browser acceptance require the remaining runtime/provider setup. Storage defaults to a private local filesystem seam; B2 remains optional and unverified without supplied credentials.
