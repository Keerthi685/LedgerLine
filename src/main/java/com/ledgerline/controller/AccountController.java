
package com.ledgerline.controller;

import com.ledgerline.entity.Account;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.service.AccountService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<?> createAccount(
            Authentication authentication) {

        Account account = accountService.createAccount(
            authentication.getName()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                    "message", "Account created successfully",
                    "accountNumber", account.getAccountNumber(),
                    "balance", account.getBalance(),
                    "currency", account.getCurrency(),
                    "status", account.getStatus()
                ));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<?> getMyAccounts(
            Authentication authentication) {

        List<Map<String, Object>> accounts =
            accountService.getMyAccounts(authentication.getName())
                .stream()
                .map(account -> Map.<String, Object>of(
                    "accountNumber", account.getAccountNumber(),
                    "balance", account.getBalance(),
                    "currency", account.getCurrency(),
                    "status", account.getStatus()
                ))
                .toList();

        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountNumber}/statement")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<?> statement(@PathVariable String accountNumber,
                                       Authentication authentication) {
        List<Map<String, Object>> rows = accountService
            .getStatement(authentication.getName(), accountNumber)
            .stream()
            .map(entry -> Map.<String, Object>of(
                "entryId", entry.getId(),
                "transactionReference", entry.getTransaction().getReference(),
                "transactionType", entry.getTransaction().getType().name(),
                "status", entry.getTransaction().getStatus().name(),
                "entryType", entry.getEntryType().name(),
                "amount", entry.getAmount(),
                "balanceAfter", entry.getBalanceAfter(),
                "actor", entry.getActor().getUsername(),
                "createdAt", entry.getCreatedAt().toString()
            )).toList();
        return ResponseEntity.ok(rows);
    }
}
