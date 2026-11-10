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
- `GET /api/auth/me` — current authenticated username and role
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


## Added role-aware views
- `GET /api/accounts/{accountNumber}/statement` — account-holder statement; verifies account ownership.
- `GET /api/audit/ledger` — read-only global ledger for ADMIN and AUDITOR roles.
- `GET /api/admin/overview` — basic counts for ADMIN.

## Optional first administrator bootstrap
Set all three variables on the application service before first start:
- `LEDGERLINE_ADMIN_USERNAME`
- `LEDGERLINE_ADMIN_EMAIL`
- `LEDGERLINE_ADMIN_PASSWORD` (at least 12 characters)

The bootstrap creates an ADMIN only if that username and email do not already exist. Public registration always creates ACCOUNT_HOLDER users. Remove the bootstrap variables after creating the administrator. Never commit their values.

## Important verification note
The dashboard is a lightweight browser client using HTTP Basic authentication. Credentials are held in page memory only and are cleared on sign-out or refresh. Use HTTPS in deployment. Before treating this as a completed challenge submission, run real MySQL integration tests for replay, concurrent withdrawals, money conservation and account isolation, and enforce append-only ledger permissions at the database layer. The entity callbacks alone do not prevent a privileged database user from changing rows.


## Database-level append-only enforcement
After the first deployment has created the `ledger_entries` table, connect to the Railway MySQL database as a database administrator and execute `database/ledger_immutability.sql`. The triggers reject normal SQL UPDATE and DELETE operations on ledger rows. Keep database administrator credentials separate from the application user and do not grant the application user permission to drop triggers.

## Integration tests
`LedgerLineIntegrityIT` uses Testcontainers with MySQL 8.4. Run it with Docker available:

```powershell
mvn -Dtest=LedgerLineIntegrityIT test
```

The tests cover idempotent replay, changed-payload conflict, concurrent withdrawals, transfer conservation, statement postings and cross-user transfer authorization. If Docker is unavailable, Testcontainers is configured to skip these integration tests; a skipped test is not evidence that the requirement passed.

### Provision an auditor account
Register the intended auditor through the normal registration endpoint, then run `database/promote_auditor.sql` as a database administrator after replacing the placeholder username. Public registration intentionally cannot choose an elevated role. The auditor can call `GET /api/audit/ledger` but cannot call account-holder transaction endpoints.
