package com.nudgebank.bankbackend.account.service;

import com.nudgebank.bankbackend.account.dto.AccountSummaryResponse;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

  private final AccountRepository accountRepository;

  public List<AccountSummaryResponse> getMyAccounts(Long memberId) {
    return accountRepository.findAllByMemberId(memberId).stream()
        .map(account -> new AccountSummaryResponse(
            account.getAccountId(),
            account.getAccountName(),
            account.getAccountNumber(),
            account.getBalance()
        ))
        .toList();
  }
}
