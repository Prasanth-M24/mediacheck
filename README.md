# MEDIACHECK

MEDIACHECK is an image-first, multi-tenant platform for AI content and rights review. Phase 1 provides a frontend shell, Spring Boot and FastAPI foundations, and local Docker infrastructure. It does not yet analyze media, authenticate users, or make rights decisions.

## Repository layout

```text
frontend/        React, TypeScript, Vite, Tailwind application shell
backend/         Spring Boot API foundation and future policy engine
ai-service/      FastAPI private analysis-service foundation
infrastructure/  Docker Compose for local services
contracts/       Versioned JSON Schemas shared by services
docs/            Architecture and setup documentation
```

## Run locally

Copy `.env.example` to `.env`, then run `docker compose --env-file .env -f infrastructure/docker-compose.yml up --build`.

See [development setup](docs/development-setup.md), [architecture](docs/architecture.md), and [the Phase 0 report](docs/phase-0-requirements-freeze.md) for details.

The Phase 2.1 database schema, tenancy model, and verification status are documented in [the database design](docs/phase-2.1-database-design.md).

## Current limitations

There is no authentication, tenant enforcement, upload workflow, Backblaze B2 client, queue producer/consumer, analysis pipeline, rights CRUD, policy engine, or production-security certification. Storage defaults to a local filesystem seam; B2 is optional, private-by-design, and unverified without supplied credentials. RabbitMQ is self-hosted open-source software in local Compose; it has no Phase 1 job processing integration.
