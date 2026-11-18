# Architecture

## Implemented Phase 1 foundation

The browser serves a React/Vite shell and requests the public Spring Boot system-info endpoint through `VITE_API_BASE_URL`. The browser does not contact the AI service directly. Spring Boot exposes health and non-sensitive service metadata. FastAPI exposes private liveness, readiness, and service-information endpoints. RabbitMQ is provisioned but no producer or worker exists yet.

## Planned workflow

`Browser -> Spring Boot API -> PostgreSQL / private Backblaze B2 storage / RabbitMQ -> private AI worker -> normalized evidence -> policy engine`

PostgreSQL will hold tenant-scoped records, evidence metadata, policy versions, decisions, and audit events; it will not store media binaries. Backblaze B2 will hold private original media and rights evidence through its S3-compatible API. It is optional locally and has no active client or public bucket configuration in Phase 1. The endpoint and region must come from the actual B2 bucket configuration.

Local development uses a configuration seam with `MEDIACHECK_STORAGE_PROVIDER=local` and `MEDIACHECK_STORAGE_PATH=./mediacheck-data`. No upload feature writes to it yet. Later deployment must use a persistent volume or bind mount rather than a container's ephemeral writable layer, and must not expose filesystem paths to browsers.

## Security boundary

Authentication, tenant authorization, secure uploads, queue authorization, policy enforcement, and audit persistence are later-phase work. The current API has no business endpoints and is not production-ready.
