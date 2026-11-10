
package com.ledgerline.service;

import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountStatus;
import com.ledgerline.entity.LedgerEntry;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.entity.AppUser;
import com.ledgerline.repository.AccountRepository;
import com.ledgerline.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public Account createAccount(String username) {

        AppUser owner = userRepository.findByUsername(username)
                .orElseThrow(() ->
                    new IllegalArgumentException("User not found"));

        Account account = new Account();

        account.setAccountNumber(
            "LL" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 18)
                .toUpperCase()
        );

        account.setOwner(owner);
        account.setBalance(BigDecimal.ZERO);
        account.setCurrency("INR");
        account.setStatus(AccountStatus.ACTIVE);

        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<Account> getMyAccounts(String username) {

        AppUser owner = userRepository.findByUsername(username)
                .orElseThrow(() ->
                    new IllegalArgumentException("User not found"));

        return accountRepository.findByOwnerId(owner.getId());
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getStatement(String username, String accountNumber) {
        AppUser owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Account not found"));
        if (!account.getOwner().getId().equals(owner.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "You cannot access this account");
        }
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtAsc(account.getId());
    }
}
