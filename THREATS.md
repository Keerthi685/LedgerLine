# THREATS.md — LedgerLine

This file records the threat model and the controls present in this source snapshot. A control is not considered proven until its test is run against MySQL.

| Threat | Current control | Evidence still required |
|---|---|---|
| Duplicate POST/retry | Per-actor idempotency key, request hash, unique key constraint, transaction record reference | Concurrent replay integration test proving one ledger movement and same response |
| Same key reused for different request | Request hash comparison returns HTTP 409 | API test for changed amount/account/body |
| Concurrent withdrawals | Pessimistic write lock on account row and transaction boundary | Two simultaneous requests against one MySQL account; assert one success, one refusal, non-negative balance |
| Partial transfer | Source and destination locked in stable account-number order; balance changes and ledger entries share transaction | Inject failure between debit and credit and verify rollback |
| Cross-account access | Server-side ownership check on source and statement endpoint | Two-user authorization integration tests |
| Negative or invalid amount | Server-side positive amount and precision validation | Boundary tests including zero, negative, >2 decimals and DECIMAL overflow |
| Ledger update/delete | JPA lifecycle callbacks reject entity update/delete | Not sufficient against direct SQL or privileged DB users; add restricted DB grants/triggers and test direct SQL denial |
| Privilege escalation | Public registration always assigns ACCOUNT_HOLDER; optional admin bootstrap uses environment variables | Verify auditor/admin endpoints deny holders and registration cannot request elevated role |
| Credential leakage | `.env` excluded by `.gitignore`; HTTP Basic used only over HTTPS in deployment | Rotate any credential previously exposed; scan Git history and deployment logs |

## Known limitations / accepted risks
- The dashboard is a demonstration client, not a production banking interface.
- HTTP Basic credentials exist in page memory while signed in; use HTTPS and sign out on shared devices.
- The current ledger entity callbacks are application-level protection, not database-level immutability.
- No claim of production readiness is made. Do not use real funds.
