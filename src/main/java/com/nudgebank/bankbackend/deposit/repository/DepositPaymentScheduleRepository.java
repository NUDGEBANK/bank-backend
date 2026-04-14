package com.nudgebank.bankbackend.deposit.repository;

import com.nudgebank.bankbackend.deposit.domain.DepositPaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepositPaymentScheduleRepository extends JpaRepository<DepositPaymentSchedule, Long> {
    List<DepositPaymentSchedule> findAllByDepositAccount_DepositAccountIdOrderByInstallmentNoAsc(Long depositAccountId);

    Optional<DepositPaymentSchedule> findFirstByDepositAccount_DepositAccountIdAndIsPaidFalseOrderByInstallmentNoAsc(Long depositAccountId);

    long countByDepositAccount_DepositAccountIdAndIsPaidTrue(Long depositAccountId);

    long countByDepositAccount_DepositAccountId(Long depositAccountId);
}
