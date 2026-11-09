
package com.ledgerline.service;

import com.ledgerline.entity.Account;
import com.ledgerline.entity.AccountStatus;
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

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
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
}
