# Development setup

Prerequisites: Node.js 20+, Java 21+, Maven 3.9+, Python 3.12+, Docker Desktop with Compose. Copy `.env.example` to `.env` and replace local passwords. Do not put Backblaze B2 credentials in frontend variables.

## Windows PowerShell

`Copy-Item .env.example .env`

`cd frontend; npm install; npm run dev`

`cd ..\backend; mvn test; mvn spring-boot:run`

`cd ..\ai-service; py -3.12 -m venv .venv; .\.venv\Scripts\Activate.ps1; pip install -r requirements-dev.txt; pytest; uvicorn app.main:app --reload`

## Linux/macOS

`cp .env.example .env`

`(cd frontend && npm install && npm run dev)`

`(cd backend && mvn test && mvn spring-boot:run)`

`(cd ai-service && python3.12 -m venv .venv && . .venv/bin/activate && pip install -r requirements-dev.txt && pytest && uvicorn app.main:app --reload)`

Start PostgreSQL and RabbitMQ before the backend, or run Compose: `docker compose --env-file .env -f infrastructure/docker-compose.yml up --build`. Use `psql -h localhost -U mediacheck_app -d mediacheck` for database access. Override ports with the `*_HOST_PORT` values in `.env`. `down` preserves database data; `down -v` destroys it intentionally.

For local-only storage leave `MEDIACHECK_STORAGE_PROVIDER=local` and use `MEDIACHECK_STORAGE_PATH=./mediacheck-data`; this directory is ignored by Git and must later be backed by persistent storage. For Backblaze B2, leave `B2_ENDPOINT`, `B2_REGION`, `B2_BUCKET_NAME`, `B2_KEY_ID`, and `B2_APPLICATION_KEY` blank until authorized credentials are available. Obtain the endpoint and region from the B2 bucket settings; B2 connectivity is not tested in Phase 1. B2's free allowance is not a guarantee that every usage pattern has zero cost.

If Docker Desktop is stopped, start it and wait until its engine is ready, then run `docker compose --env-file .env -f infrastructure/docker-compose.yml up --build`. Do not use `down -v` unless you explicitly intend to delete the PostgreSQL volume.
