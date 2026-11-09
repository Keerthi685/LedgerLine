
package com.ledgerline.repository;

import com.ledgerline.entity.IdempotencyRecord;
import com.ledgerline.entity.IdempotencyStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyRepository
        extends JpaRepository<IdempotencyRecord, Long> {

    @Modifying
    @Query(value = """
        INSERT IGNORE INTO idempotency_keys
        (
            actor_id,
            idempotency_key,
            request_hash,
            status,
            created_at
        )
        VALUES (
            :actorId,
            :key,
            :requestHash,
            'IN_PROGRESS',
            NOW()
        )
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("actorId") Long actorId,
        @Param("key") String key,
        @Param("requestHash") String requestHash
    );

    @Query(value = """
        SELECT *
        FROM idempotency_keys
        WHERE actor_id = :actorId
          AND idempotency_key = :key
        FOR UPDATE
        """, nativeQuery = true)
    Optional<IdempotencyRecord> findForUpdate(
        @Param("actorId") Long actorId,
        @Param("key") String key
    );
}
