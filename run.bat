@echo off
set DB_URL=jdbc:mysql://localhost:3306/ledgerline_db
set DB_USERNAME=ledgerline_user
set DB_PASSWORD=LedgerLine_App_P@ss1
mvn spring-boot:run > app.log 2>&1
