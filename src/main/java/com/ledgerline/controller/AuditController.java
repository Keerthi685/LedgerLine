package com.ledgerline.controller;

import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.repository.LedgerEntryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final LedgerEntryRepository ledgerRepository;
    public AuditController(LedgerEntryRepository ledgerRepository) { this.ledgerRepository = ledgerRepository; }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public ResponseEntity<?> ledger() {
        List<Map<String,Object>> rows = ledgerRepository.findAllByOrderByCreatedAtDesc()
            .stream().map(e -> Map.<String,Object>of(
                "entryId", e.getId(),
                "reference", e.getTransaction().getReference(),
                "accountNumber", e.getAccount().getAccountNumber(),
                "entryType", e.getEntryType().name(),
                "amount", e.getAmount(),
                "balanceAfter", e.getBalanceAfter(),
                "actor", e.getActor().getUsername(),
                "createdAt", e.getCreatedAt().toString()
            )).toList();
        return ResponseEntity.ok(rows);
    }
}
