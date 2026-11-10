-- Run as a database administrator after registering the intended auditor account.
-- Replace the username before executing. Public registration cannot assign this role.
UPDATE users
SET role = 'AUDITOR'
WHERE username = 'REPLACE_WITH_AUDITOR_USERNAME';

-- Verify exactly one account was updated:
SELECT id, username, role FROM users
WHERE username = 'REPLACE_WITH_AUDITOR_USERNAME';
