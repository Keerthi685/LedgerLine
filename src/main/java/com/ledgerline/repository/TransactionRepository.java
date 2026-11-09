
package com.ledgerline.repository;

import com.ledgerline.entity.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface TransactionRepository
        extends JpaRepository<TransactionRecord, Long> {

    Optional<TransactionRecord> findByReference(String reference);

    List<TransactionRecord> findByActorIdOrderByCreatedAtDesc(Long actorId);
}
