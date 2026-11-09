
package com.ledgerline.repository;

import com.ledgerline.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtAsc(Long accountId);

    List<LedgerEntry> findByTransactionId(Long transactionId);
}
