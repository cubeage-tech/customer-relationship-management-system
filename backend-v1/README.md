# CRM Backend

Spring Boot 3.5 / Java 21 backend for the CRM system. This is an early-stage starter — most modules are
scaffolded folders awaiting implementation; only `/api/test` is wired up so far.

## Prerequisites

- Java 21 (JDK)
- Docker (for the local Postgres database), or a Postgres instance of your own

Maven itself is not required — this project ships the Maven Wrapper (`mvnw` / `mvnw.cmd`).

## Running locally

1. Copy the environment template and adjust if needed:

   ```
   cp .env.example .env
   ```

2. Start the database:

   ```
   docker compose up -d
   ```

3. Run the app:

   ```
   ./mvnw spring-boot:run       # macOS/Linux
   mvnw.cmd spring-boot:run     # Windows
   ```

4. Verify it's up:

   ```
   curl http://localhost:8080/api/test
   ```

   Should return `CRM Backend is running!`.

The app defaults to the `dev` Spring profile (see `application-dev.yml`), which points at the Postgres
container started via `docker-compose.yml`.

## Tests

```
./mvnw test
```
