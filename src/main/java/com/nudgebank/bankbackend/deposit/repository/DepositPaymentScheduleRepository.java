package com.nudgebank.bankbackend.deposit.repository;

import com.nudgebank.bankbackend.deposit.domain.DepositPaymentSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepositPaymentScheduleRepository extends JpaRepository<DepositPaymentSchedule, Long> {
    List<DepositPaymentSchedule> findAllByDepositAccount_DepositAccountIdOrderByInstallmentNoAsc(Long depositAccountId);

    Optional<DepositPaymentSchedule> findFirstByDepositAccount_DepositAccountIdAndIsPaidFalseOrderByInstallmentNoAsc(Long depositAccountId);

    @Query("""
        select schedule.depositPaymentScheduleId
        from DepositPaymentSchedule schedule
        join schedule.depositAccount depositAccount
        where schedule.autoTransferYn = true
          and schedule.isPaid = false
          and schedule.autoTransferDay = :dayOfMonth
          and depositAccount.status = :accountStatus
        order by depositAccount.depositAccountId asc, schedule.installmentNo asc
        """)
    List<Long> findDueAutoTransferScheduleIds(
        @Param("dayOfMonth") Integer dayOfMonth,
        @Param("accountStatus") String accountStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select schedule
        from DepositPaymentSchedule schedule
        join fetch schedule.depositAccount depositAccount
        join fetch depositAccount.account linkedAccount
        where schedule.depositPaymentScheduleId = :depositPaymentScheduleId
        """)
    Optional<DepositPaymentSchedule> findByIdForUpdate(@Param("depositPaymentScheduleId") Long depositPaymentScheduleId);

    long countByDepositAccount_DepositAccountIdAndIsPaidTrue(Long depositAccountId);

    long countByDepositAccount_DepositAccountId(Long depositAccountId);
}
