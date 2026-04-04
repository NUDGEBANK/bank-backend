package com.nudgebank.bankbackend.account.repository;

import com.nudgebank.bankbackend.account.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
  boolean existsByAccountNumber(String accountNumber);
  List<Account> findAllByMemberId(Long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.accountId = :accountId")
  Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);
}