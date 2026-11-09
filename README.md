# LedgerLine

LedgerLine is a Spring Boot + MySQL learning project for account management and money movement.

## Requirements
- JDK 17+
- Maven 3.9+
- MySQL 8+ (or Docker Desktop with Compose)
- VS Code (Eclipse is not required)

## Run locally with an existing MySQL server

1. Create the database and application user. Run the SQL below in MySQL as an administrator:

```sql
CREATE DATABASE IF NOT EXISTS ledgerline_db;
CREATE USER IF NOT EXISTS 'ledgerline_user'@'%' IDENTIFIED BY 'CHANGE_THIS_PASSWORD';
GRANT ALL PRIVILEGES ON ledgerline_db.* TO 'ledgerline_user'@'%';
FLUSH PRIVILEGES;
```

2. In PowerShell, from the project root:

```powershell
$env:DB_PASSWORD = "the_password_you_set"
$env:DB_URL = "jdbc:mysql://localhost:3306/ledgerline_db"
$env:DB_USERNAME = "ledgerline_user"
mvn clean package
mvn spring-boot:run
```

3. Open `http://localhost:8080/`. Health endpoint: `http://localhost:8080/api/health`.

## Run with Docker Compose

1. Install and start Docker Desktop.
2. Copy `.env.example` to `.env` and set two strong, different passwords.
3. From the project root run:

```powershell
docker compose up --build -d
docker compose ps
docker compose logs -f app
```

4. Open `http://localhost:8080/`.

Stop containers (database data is retained in the named volume):

```powershell
docker compose down
```

Stop and delete the database volume (DESTRUCTIVE; deletes local database data):

```powershell
docker compose down -v
```

## API overview

- `GET /api/health` — public health check
- `POST /api/auth/register` — register an account-holder user
- `POST /api/accounts` — create an account (HTTP Basic; ACCOUNT_HOLDER)
- `GET /api/accounts/my` — list the signed-in holder's accounts
- `POST /api/transactions/deposit/{accountNumber}` — deposit; requires `Idempotency-Key`
- `POST /api/transactions/withdraw/{accountNumber}` — withdraw; requires `Idempotency-Key`
- `POST /api/transactions/transfer/{sourceAccountNumber}` — transfer; requires `Idempotency-Key`

Transaction request JSON:

```json
{"amount": 500.00}
```

Transfer request JSON:

```json
{"destinationAccount": "LL...", "amount": 100.00}
```

Use HTTP Basic authentication with the registered account-holder username and password.

## Important implementation status

This source snapshot contains registration, account creation/listing, deposit, withdrawal, transfer, account ownership checks, pessimistic account-row locking, request idempotency records, transaction records and ledger-entry records.

This is not yet a production banking system or a fully verified challenge submission. In particular, admin/auditor workflows, statement endpoints, integration tests that prove race conditions and money conservation, stronger database-level append-only enforcement, production authentication (for example JWT/TLS/rate limiting), deployment to a public host, and a completed threat/prompt evidence trail still need implementation and verification. Do not use real funds or production credentials.
