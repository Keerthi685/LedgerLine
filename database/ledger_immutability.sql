-- Apply once as a database administrator after the ledger_entries table exists.
DELIMITER $$
CREATE TRIGGER ledger_entries_no_update
BEFORE UPDATE ON ledger_entries FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger_entries are append-only';
END$$
CREATE TRIGGER ledger_entries_no_delete
BEFORE DELETE ON ledger_entries FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger_entries are append-only';
END$$
DELIMITER ;
