package com.nudgebank.bankbackend.account.repository;

import com.nudgebank.bankbackend.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {
  boolean existsByAccountNumber(String accountNumber);
  List<Account> findAllByMemberId(Long memberId);
}