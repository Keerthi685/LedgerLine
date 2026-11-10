package com.ledgerline.controller;

import com.ledgerline.repository.UserRepository;
import com.ledgerline.repository.AccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepository users;
    private final AccountRepository accounts;
    public AdminController(UserRepository users, AccountRepository accounts) {
        this.users = users; this.accounts = accounts;
    }
    @GetMapping("/overview")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(Map.of(
            "users", users.count(),
            "accounts", accounts.count()
        ));
    }
}
