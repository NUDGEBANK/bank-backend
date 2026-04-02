package com.nudgebank.bankbackend.account.repository;

import com.nudgebank.bankbackend.account.domain.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
  boolean existsByAccountNumber(String accountNumber);
  Optional<Account> findByMemberId(Long memberId);
  List<Account> findAllByMemberId(Long memberId);
}
