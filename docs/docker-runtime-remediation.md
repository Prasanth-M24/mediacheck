# Docker Runtime and Database Remediation Report

**Date:** 2026-11-01  
**Author:** MEDIACHECK Engineering  
**Scope:** Stage 1 Diagnostics & Remediation for Docker Compose runtime, PostgreSQL authentication roles, SQL column schema consistency, and container lifecycle.

---

## 1. Executive Summary

All reported runtime anomalies have been thoroughly investigated, diagnosed, and remediated:
1. **Docker Engine Availability:** The Docker Desktop Linux engine (`desktop-linux`, `npipe:////./pipe/dockerDesktopLinuxEngine`) is online and functional. Compose configuration validates cleanly with exit code 0.
2. **PostgreSQL Roles:** The `FATAL: role "mediacheck_app" does not exist` and `FATAL: role "mediacheck" does not exist` errors did not originate from the application. The running PostgreSQL container was initialized with `POSTGRES_USER=postgres` as configured in `.env`. Manual troubleshooting queries attempted to use default names (`mediacheck_app` / `mediacheck`), which failed authentication.
3. **Diagnostic SQL Queries:** Manual queries failed due to column name assumptions (`u.is_email_verified` instead of `u.email_verified`; `m.role` instead of `m.membership_role`). The actual database tables and JPA entity mappings match Flyway V1–V3 migrations.
4. **Service Shutdowns:** Previous container stops were orderly, graceful shutdowns (exit codes 0 and 143/SIGTERM), not unexpected crashes or restart loops.
5. **Runtime Health:** All 5 Docker Compose services (`postgres`, `rabbitmq`, `backend`, `frontend`, `ai-service`) are healthy and verified responding on their respective host ports.

---

## 2. Issue Investigations & Root Causes

### Issue A — Docker Engine Availability
- **Active Context:** `desktop-linux *` pointing to `npipe:////./pipe/dockerDesktopLinuxEngine`.
- **Docker Engine Version:** Docker Engine 29.9.2 (Docker Desktop 4.97.0).
- **Compose Validation:** `docker compose --env-file .env -f infrastructure/docker-compose.yml config --quiet` executed with exit code `0`.
- **Root Cause of Prior Blocker:** Temporary unavailability of the named pipe prior to Docker Desktop daemon startup. Once Docker Desktop is running, the pipe is accessible and all Compose commands execute successfully.

### Issue B — PostgreSQL Role Errors
- **Datasource Configuration:**
  - `SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/mediacheck`
  - `SPRING_DATASOURCE_USERNAME: postgres` (resolved from `.env` `POSTGRES_USER=postgres`)
  - `SPRING_DATASOURCE_PASSWORD: [REDACTED]`
- **PostgreSQL Database Roles:**
  ```text
  Role name: postgres (Superuser, Create role, Create DB)
  ```
- **Root Cause:** The `postgres-data` volume was initialized with `POSTGRES_USER=postgres`. When manual diagnostic commands were run without referencing `.env`, commands guessed `psql -U mediacheck_app` or `-U mediacheck`. The application itself connects using `postgres` and has 0 authentication errors.

### Issue C — Schema & Column Name Assumptions
- **User Verification Flag:**
  - Flyway V2 definition: `ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;`
  - Entity mapping: `PlatformUser.java` -> `email_verified` (getter: `isEmailVerified()`).
  - Correct query: `SELECT email_verified FROM users;` (not `is_email_verified`).
- **Membership Role:**
  - Flyway V1 definition: `membership_role VARCHAR(32) NOT NULL`
  - Entity mapping: `CompanyMembership.java` -> `membership_role`.
  - Correct query: `SELECT membership_role FROM company_memberships;` (not `role`).
- **Verdict:** No application or Flyway schema mismatch exists. Diagnostic scripts were updated to reflect the canonical column names.

### Issue D — Container Lifecycle & Shutdown Verification
- Inspection of `docker compose ps -a` before startup:
  - `infrastructure-backend-1`: `Exited (143)` (SIGTERM graceful stop).
  - `infrastructure-frontend-1`: `Exited (0)` (clean shutdown).
  - `infrastructure-postgres-1`: `Exited (0)` (clean shutdown).
  - `infrastructure-rabbitmq-1`: `Exited (0)` (clean shutdown).
  - `infrastructure-ai-service-1`: `Exited (0)` (clean shutdown).
- **Restart count:** 0 restart loops.
- **Verdict:** Clean shutdowns initiated by Docker Compose stop.

---

## 3. Verified Runtime State

All containers were started with `docker compose --env-file .env -f infrastructure/docker-compose.yml up -d` and verified:

| Container | Service | Host Port | Status | Health / Response |
|---|---|---|---|---|
| `infrastructure-postgres-1` | `postgres` | `127.0.0.1:5433` | Up (healthy) | `pg_isready -U postgres -d mediacheck` -> accepting connections |
| `infrastructure-rabbitmq-1` | `rabbitmq` | `127.0.0.1:5673`, `127.0.0.1:15673` | Up (healthy) | Management UI returns `HTTP 200` |
| `infrastructure-backend-1` | `backend` | `127.0.0.1:8080` | Up | `/actuator/health` -> `{"status":"UP"}` |
| `infrastructure-frontend-1` | `frontend` | `127.0.0.1:5173` | Up | Nginx SPA returns `HTTP 200` |
| `infrastructure-ai-service-1` | `ai-service` | `8000/tcp` (internal) | Up | `/health/live` -> `{"status":"UP"}`; `/health/ready` -> `ready: true` |

### Database Preservation Check
- The existing `mediacheck` database volume was strictly preserved.
- Existing company and demo user accounts remain active in the database:
  - User: `demo@mediacheck.com` (verified, active)
  - Company: `Demo Media Corp`
  - Membership: `OWNER`
