CREATE DATABASE IF NOT EXISTS ledgerline_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- Select this database before running the application.
-- Hibernate currently creates/updates tables from the JPA entities.
USE ledgerline_db;
