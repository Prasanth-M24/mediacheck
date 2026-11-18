# Local Docker Compose

Run from the repository root: `docker compose --env-file .env -f infrastructure/docker-compose.yml up --build`.

The frontend is at `http://localhost:5173`, backend at `http://localhost:8080`, RabbitMQ management at `http://localhost:15672`, and PostgreSQL at `localhost:5432`. RabbitMQ AMQP uses `5672`.

All exposed infrastructure ports bind to `127.0.0.1`. The AI service remains internal-only. `postgres-data` persists through `docker compose down`; remove it intentionally with `docker compose --env-file .env -f infrastructure/docker-compose.yml down -v`.

The backend defaults to the local storage configuration seam (`MEDIACHECK_STORAGE_PROVIDER=local`). Backblaze B2 is optional and remains inactive unless its configuration is explicitly supplied; no B2 client, bucket operation, or public access is configured in Phase 1.
