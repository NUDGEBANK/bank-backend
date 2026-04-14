package com.nudgebank.bankbackend.deposit.repository;

import com.nudgebank.bankbackend.deposit.domain.DepositTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepositTransactionRepository extends JpaRepository<DepositTransaction, Long> {
    List<DepositTransaction> findTop20ByDepositAccount_DepositAccountIdOrderByTransactionDatetimeDesc(Long depositAccountId);
}
