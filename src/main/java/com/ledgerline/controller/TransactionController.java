
package com.ledgerline.controller;

import com.ledgerline.dto.MoneyRequest;
import com.ledgerline.dto.TransferRequest;
import com.ledgerline.entity.TransactionRecord;
import com.ledgerline.service.TransactionService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(
            TransactionService transactionService) {

        this.transactionService = transactionService;
    }

    // ================= DEPOSIT =================

    @PostMapping("/deposit/{accountNumber}")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<?> deposit(
            @PathVariable String accountNumber,
            @Valid @RequestBody MoneyRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {

        TransactionRecord result = transactionService.deposit(
            authentication.getName(),
            accountNumber,
            request.amount(),
            idempotencyKey
        );

        return ResponseEntity.ok(response(result));
    }

    // ================= WITHDRAWAL =================

    @PostMapping("/withdraw/{accountNumber}")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<?> withdraw(
            @PathVariable String accountNumber,
            @Valid @RequestBody MoneyRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {

        TransactionRecord result = transactionService.withdraw(
            authentication.getName(),
            accountNumber,
            request.amount(),
            idempotencyKey
        );

        return ResponseEntity.ok(response(result));
    }

    // ================= TRANSFER =================

    @PostMapping("/transfer/{sourceAccountNumber}")
    @PreAuthorize("hasRole('ACCOUNT_HOLDER')")
    public ResponseEntity<?> transfer(
            @PathVariable String sourceAccountNumber,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication authentication) {

        TransactionRecord result = transactionService.transfer(
            authentication.getName(),
            sourceAccountNumber,
            request.destinationAccount(),
            request.amount(),
            idempotencyKey
        );

        return ResponseEntity.ok(response(result));
    }

    // ================= RESPONSE =================

    private Map<String, Object> response(
            TransactionRecord transaction) {

        return Map.of(
            "reference", transaction.getReference(),
            "type", transaction.getType(),
            "status", transaction.getStatus(),
            "amount", transaction.getAmount(),
            "failureReason",
            transaction.getFailureReason() == null
                ? "" : transaction.getFailureReason()
        );
    }
}
