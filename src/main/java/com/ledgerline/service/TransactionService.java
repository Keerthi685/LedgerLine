
package com.ledgerline.service;

import com.ledgerline.entity.*;
import com.ledgerline.repository.*;
import com.ledgerline.entity.IdempotencyRecord;
import com.ledgerline.entity.IdempotencyStatus;
import com.ledgerline.repository.IdempotencyRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyRepository idempotencyRepository;

    public TransactionService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            IdempotencyRepository idempotencyRepository) {

        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    // ================= DEPOSIT =================

    @Transactional
    public TransactionRecord deposit(
            String username,
            String accountNumber,
            BigDecimal amount,
            String idempotencyKey) {

        validateAmount(amount);

        AppUser actor = findUser(username);

        String requestHash = createRequestHash(
            "DEPOSIT",
            accountNumber,
            amount.stripTrailingZeros().toPlainString()
        );

        IdempotencyClaim claim = claimIdempotency(
            actor, idempotencyKey, requestHash
        );

        TransactionRecord replay = replayIfDuplicate(claim);

        if (replay != null) {
            return replay;
        }

        Account account = accountRepository
            .findByAccountNumberForUpdate(accountNumber)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Account not found"
            ));

        verifyOwnership(account, actor);
        verifyActive(account);

        BigDecimal newBalance = account.getBalance().add(amount);

        account.setBalance(newBalance);

        TransactionRecord transaction = new TransactionRecord();
        transaction.setReference(generateReference());
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setAmount(amount);
        transaction.setDestinationAccount(account);
        transaction.setActor(actor);

        transactionRepository.save(transaction);

        saveLedgerEntry(
            transaction,
            account,
            LedgerEntryType.CREDIT,
            amount,
            newBalance,
            actor
        );

        completeIdempotency(claim, transaction);

        return transaction;
    }

    // ================= WITHDRAWAL =================

    @Transactional
    public TransactionRecord withdraw(
            String username,
            String accountNumber,
            BigDecimal amount,
            String idempotencyKey) {

        validateAmount(amount);

        AppUser actor = findUser(username);

        String requestHash = createRequestHash(
            "WITHDRAWAL",
            accountNumber,
            amount.stripTrailingZeros().toPlainString()
        );

        IdempotencyClaim claim = claimIdempotency(
            actor, idempotencyKey, requestHash
        );

        TransactionRecord replay = replayIfDuplicate(claim);

        if (replay != null) {
            return replay;
        }

        Account account = accountRepository
            .findByAccountNumberForUpdate(accountNumber)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Account not found"
            ));

        verifyOwnership(account, actor);
        verifyActive(account);

        BigDecimal currentBalance = account.getBalance();

        TransactionRecord transaction = new TransactionRecord();
        transaction.setReference(generateReference());
        transaction.setType(TransactionType.WITHDRAWAL);
        transaction.setAmount(amount);
        transaction.setSourceAccount(account);
        transaction.setActor(actor);

        // Record insufficient-balance attempts.
        if (currentBalance.compareTo(amount) < 0) {

            transaction.setStatus(TransactionStatus.REFUSED);
            transaction.setFailureReason("Insufficient balance");

            transactionRepository.save(transaction);

            saveLedgerEntry(
                transaction,
                account,
                LedgerEntryType.REFUSED,
                amount,
                currentBalance,
                actor
            );

            completeIdempotency(claim, transaction);

            return transaction;
        }

        BigDecimal newBalance = currentBalance.subtract(amount);

        account.setBalance(newBalance);

        transaction.setStatus(TransactionStatus.SUCCESS);

        transactionRepository.save(transaction);

        saveLedgerEntry(
            transaction,
            account,
            LedgerEntryType.DEBIT,
            amount,
            newBalance,
            actor
        );

        completeIdempotency(claim, transaction);

        return transaction;
    }

    // ================= TRANSFER =================

    @Transactional
    public TransactionRecord transfer(
            String username,
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal amount,
            String idempotencyKey) {

        validateAmount(amount);

        if (sourceAccountNumber.equals(destinationAccountNumber)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Source and destination accounts must be different"
            );
        }

        AppUser actor = findUser(username);

        String requestHash = createRequestHash(
            "TRANSFER",
            sourceAccountNumber,
            destinationAccountNumber,
            amount.stripTrailingZeros().toPlainString()
        );

        IdempotencyClaim claim = claimIdempotency(
            actor, idempotencyKey, requestHash
        );

        TransactionRecord replay = replayIfDuplicate(claim);

        if (replay != null) {
            return replay;
        }

        // Lock accounts in consistent order to reduce deadlocks.
        String firstNumber = sourceAccountNumber.compareTo(
            destinationAccountNumber) < 0
                ? sourceAccountNumber : destinationAccountNumber;

        String secondNumber = sourceAccountNumber.compareTo(
            destinationAccountNumber) < 0
                ? destinationAccountNumber : sourceAccountNumber;

        Account first = accountRepository
            .findByAccountNumberForUpdate(firstNumber)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Account not found"
            ));

        Account second = accountRepository
            .findByAccountNumberForUpdate(secondNumber)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Account not found"
            ));

        Account source = first.getAccountNumber()
            .equals(sourceAccountNumber) ? first : second;

        Account destination = first.getAccountNumber()
            .equals(destinationAccountNumber) ? first : second;

        verifyOwnership(source, actor);
        verifyActive(source);
        verifyActive(destination);

        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Account currencies must match"
            );
        }

        TransactionRecord transaction = new TransactionRecord();
        transaction.setReference(generateReference());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setAmount(amount);
        transaction.setSourceAccount(source);
        transaction.setDestinationAccount(destination);
        transaction.setActor(actor);

        BigDecimal sourceBalance = source.getBalance();

        // Record refused transfers without changing balances.
        if (sourceBalance.compareTo(amount) < 0) {

            transaction.setStatus(TransactionStatus.REFUSED);
            transaction.setFailureReason("Insufficient balance");

            transactionRepository.save(transaction);

            saveLedgerEntry(
                transaction,
                source,
                LedgerEntryType.REFUSED,
                amount,
                sourceBalance,
                actor
            );

            completeIdempotency(claim, transaction);

            return transaction;
        }

        BigDecimal newSourceBalance =
            sourceBalance.subtract(amount);

        BigDecimal newDestinationBalance =
            destination.getBalance().add(amount);

        source.setBalance(newSourceBalance);
        destination.setBalance(newDestinationBalance);

        transaction.setStatus(TransactionStatus.SUCCESS);

        transactionRepository.save(transaction);

        // Debit entry for sender.
        saveLedgerEntry(
            transaction,
            source,
            LedgerEntryType.DEBIT,
            amount,
            newSourceBalance,
            actor
        );

        // Credit entry for receiver.
        saveLedgerEntry(
            transaction,
            destination,
            LedgerEntryType.CREDIT,
            amount,
            newDestinationBalance,
            actor
        );

        completeIdempotency(claim, transaction);

        return transaction;
    }

    // ================= IDEMPOTENCY =================

    private record IdempotencyClaim(
        IdempotencyRecord record,
        boolean isNew
    ) {}

    private IdempotencyClaim claimIdempotency(
            AppUser actor,
            String key,
            String requestHash) {

        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "A valid Idempotency-Key header is required"
            );
        }

        int inserted = idempotencyRepository.insertIfAbsent(
            actor.getId(),
            key,
            requestHash
        );

        IdempotencyRecord record = idempotencyRepository
            .findForUpdate(actor.getId(), key)
            .orElseThrow(() -> new IllegalStateException(
                "Could not retrieve idempotency record"
            ));

        if (inserted == 1) {
            return new IdempotencyClaim(record, true);
        }

        if (!record.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Idempotency key already used for a different request"
            );
        }

        if (record.getStatus() != IdempotencyStatus.COMPLETED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Request is already being processed"
            );
        }

        return new IdempotencyClaim(record, false);
    }

    private TransactionRecord replayIfDuplicate(
            IdempotencyClaim claim) {

        if (claim.isNew()) {
            return null;
        }

        String reference =
            claim.record().getTransactionReference();

        return transactionRepository.findByReference(reference)
            .orElseThrow(() -> new IllegalStateException(
                "Original transaction could not be found"
            ));
    }

    private void completeIdempotency(
            IdempotencyClaim claim,
            TransactionRecord transaction) {

        IdempotencyRecord record = claim.record();

        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setTransactionReference(transaction.getReference());
        record.setCompletedAt(LocalDateTime.now());

        idempotencyRepository.save(record);
    }

    private String createRequestHash(String... values) {

        try {
            String canonicalRequest = String.join("|", values);

            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                canonicalRequest.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 algorithm is unavailable", exception
            );
        }
    }

    // ================= LEDGER =================

    private void saveLedgerEntry(
            TransactionRecord transaction,
            Account account,
            LedgerEntryType type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            AppUser actor) {

        LedgerEntry entry = new LedgerEntry();

        entry.setTransaction(transaction);
        entry.setAccount(account);
        entry.setEntryType(type);
        entry.setAmount(amount);
        entry.setBalanceAfter(balanceAfter);
        entry.setActor(actor);

        ledgerEntryRepository.save(entry);
    }

    // ================= VALIDATION =================

    private AppUser findUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found"
            ));
    }

    private void verifyOwnership(Account account, AppUser actor) {

        if (!account.getOwner().getId().equals(actor.getId())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You cannot access this account"
            );
        }
    }

    private void verifyActive(Account account) {

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Account is not active"
            );
        }
    }

    private void validateAmount(BigDecimal amount) {

        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.scale() > 2
                || amount.precision() > 19) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid transaction amount"
            );
        }
    }

    private String generateReference() {
        return "TXN-" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .toUpperCase();
    }
}
