
package com.ledgerline.controller;

import com.ledgerline.entity.Account;
import com.ledgerline.service.AccountService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
}
