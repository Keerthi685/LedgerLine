# THREATS.md — LedgerLine
Initial risks to investigate:
- Duplicate request/idempotency replay.
- Concurrent withdrawals and overdrafts.
- A user attempting to transfer from another user's account.
- Partial debit/credit if an operation fails.
- Ledger updates/deletes or privileged database tampering.
- Brute-force login, credential exposure and CSRF.
For each risk, document the implemented control and a test that demonstrates it. Do not claim a risk is closed until the test has been run.
