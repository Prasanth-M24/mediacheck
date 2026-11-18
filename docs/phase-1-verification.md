# Phase 1 verification

This report is updated only with commands actually run in this workspace.

| Area | Command | Result | Status |
| --- | --- | --- | --- |
| Phase 0 contract JSON syntax | `Get-Content ... \| ConvertFrom-Json` | All four JSON files parsed | PASS |
| Frontend install | `npm ci` | Completed using the committed lockfile | PASS |
| Frontend build | `npm run build` | TypeScript and Vite production build passed | PASS |
| Frontend lint | `npm run lint` | ESLint passed | PASS |
| Backend tests | `.\\mvnw.cmd test` | 1 test passed after storage configuration alignment | PASS |
| Backend package | `.\\mvnw.cmd package -DskipTests` | Repackaged Spring Boot JAR built after storage configuration alignment | PASS |
| Backend runtime and health | Not run | Containerized backend build stalled in Maven dependency download before containers started | BLOCKED |
| AI service tests | `.\\.venv\\Scripts\\python.exe -m pytest -q` | 2 tests passed; one third-party deprecation warning | PASS |
| AI service runtime | `uvicorn ...` and `Invoke-RestMethod` | `/health/live` and `/api/v1/info` returned expected JSON | PASS |
| Compose syntax | `docker compose --env-file .env -f infrastructure/docker-compose.yml config --quiet` | Validated successfully | PASS |
| Docker engine | `docker info --format '{{.ServerVersion}}'` | Docker Desktop engine reported `29.9.2` | PASS |
| Docker image builds | `docker compose ... up --build -d` | Frontend and AI-service images completed; backend Maven dependency layer stalled before containers started | BLOCKED |
| Backblaze B2 connectivity | Not attempted | No credentials supplied; optional configuration seam only | NOT RUN |

## QA revalidation (2026-10-28)

| Area | Command or method | Result | Status |
| --- | --- | --- | --- |
| Phase 0 contract syntax | `ConvertFrom-Json` on all contract JSON files | Four files parsed successfully | PASS |
| FastAPI tests | `.\.venv\Scripts\python.exe -m pytest -q` | 2 passed; Starlette deprecation and ignored-cache write warnings only | PASS |
| FastAPI live endpoint | `GET /health/live` on localhost | HTTP 200, `{"status":"UP"}` | PASS |
| FastAPI ready endpoint | `GET /health/ready` on localhost | HTTP 200; reports process readiness only | PASS |
| FastAPI info endpoint | `GET /api/v1/info` on localhost | HTTP 200; safe service/version fields only | PASS |
| Frontend rerun | `npm ci`, `npm run build`, `npm run lint` | Sandbox denied Node access to the user-profile path | BLOCKED |
| Maven Wrapper rerun | `.\mvnw.cmd test` | Sandbox wrapper invocation failed before Maven started | BLOCKED |
| Maven rerun | `mvn test`, `mvn package -DskipTests` | Sandbox denied Maven Central network access | BLOCKED |
| Docker runtime rerun | `docker compose ... ps` | Sandbox denied Docker named-pipe access | BLOCKED |
