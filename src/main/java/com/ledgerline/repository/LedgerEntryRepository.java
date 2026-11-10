package com.ledgerline.repository;

import com.ledgerline.entity.LedgerEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    @EntityGraph(attributePaths = {"transaction", "account", "actor"})
    List<LedgerEntry> findByAccountIdOrderByCreatedAtAsc(Long accountId);

    List<LedgerEntry> findByTransactionId(Long transactionId);

    @EntityGraph(attributePaths = {"transaction", "account", "actor"})
    List<LedgerEntry> findAllByOrderByCreatedAtDesc();
}
