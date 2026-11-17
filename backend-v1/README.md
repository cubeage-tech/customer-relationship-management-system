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

## Customer documents

Customer documents are stored outside the public web root. Set `CUSTOMER_DOCUMENTS_DIR` to a
private persistent directory or volume for the running application. Uploads are limited to 10 MB
and support PDF, PNG, JPG, TXT, DOCX, and XLSX files.

- `GET /api/customers/{customerId}/documents` lists documents visible to the caller.
- `POST /api/customers/{customerId}/documents` uploads multipart form data using the `file` field.
- `GET /api/customers/{customerId}/documents/{documentId}/download` downloads a file.
- `DELETE /api/customers/{customerId}/documents/{documentId}` deletes a file.

All document routes enforce tenant and customer-owner data scope. Upload and delete are limited to
tenant admins, sales managers, and sales executives.
