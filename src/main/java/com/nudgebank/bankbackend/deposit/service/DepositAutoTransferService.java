package com.nudgebank.bankbackend.deposit.service;

import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.deposit.domain.DepositAccount;
import com.nudgebank.bankbackend.deposit.domain.DepositPaymentSchedule;
import com.nudgebank.bankbackend.deposit.domain.DepositTransaction;
import com.nudgebank.bankbackend.deposit.repository.DepositPaymentScheduleRepository;
import com.nudgebank.bankbackend.deposit.repository.DepositTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepositAutoTransferService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String TRANSACTION_PAY = "PAY";
    private static final String TRANSACTION_STATUS_COMPLETED = "COMPLETED";

    private final AccountRepository accountRepository;
    private final DepositPaymentScheduleRepository depositPaymentScheduleRepository;
    private final DepositTransactionRepository depositTransactionRepository;

    @Transactional(readOnly = true)
    public List<Long> findDueScheduleIds(LocalDate today) {
        return depositPaymentScheduleRepository.findDueAutoTransferScheduleIds(today.getDayOfMonth(), STATUS_ACTIVE);
    }

    @Transactional
    public void executeAutoTransfer(Long depositPaymentScheduleId, LocalDate today) {
        DepositPaymentSchedule schedule = depositPaymentScheduleRepository.findByIdForUpdate(depositPaymentScheduleId)
            .orElseThrow(() -> new EntityNotFoundException("자동이체 대상을 찾을 수 없습니다."));

        if (!isExecutable(schedule, today)) {
            return;
        }

        DepositAccount depositAccount = schedule.getDepositAccount();
        DepositPaymentSchedule firstUnpaidSchedule = depositPaymentScheduleRepository
            .findFirstByDepositAccount_DepositAccountIdAndIsPaidFalseOrderByInstallmentNoAsc(
                depositAccount.getDepositAccountId()
            )
            .orElse(null);
        if (firstUnpaidSchedule == null
            || !depositPaymentScheduleId.equals(firstUnpaidSchedule.getDepositPaymentScheduleId())) {
            return;
        }

        Account linkedAccount = accountRepository.findByIdForUpdate(schedule.getAccount().getAccountId())
            .orElseThrow(() -> new EntityNotFoundException("연결 계좌를 찾을 수 없습니다."));

        BigDecimal amount = scale(schedule.getPlannedAmount());
        try {
            linkedAccount.withdraw(amount);
        } catch (IllegalStateException exception) {
            schedule.markAutoTransferFailed();
            return;
        }

        depositAccount.receivePayment(amount);
        schedule.markPaid(amount, OffsetDateTime.now());
        depositTransactionRepository.save(createTransaction(depositAccount, schedule, linkedAccount, amount));
    }

    private boolean isExecutable(DepositPaymentSchedule schedule, LocalDate today) {
        if (!Boolean.TRUE.equals(schedule.getAutoTransferYn()) || Boolean.TRUE.equals(schedule.getIsPaid())) {
            return false;
        }
        if (!schedule.getDepositAccount().isActive()) {
            return false;
        }
        if (schedule.getAutoTransferDay() == null || schedule.getAutoTransferDay() != today.getDayOfMonth()) {
            return false;
        }
        return !YearMonth.from(schedule.getDueDate()).isAfter(YearMonth.from(today));
    }

    private DepositTransaction createTransaction(
        DepositAccount depositAccount,
        DepositPaymentSchedule schedule,
        Account linkedAccount,
        BigDecimal amount
    ) {
        return DepositTransaction.builder()
            .depositAccount(depositAccount)
            .depositPaymentSchedule(schedule)
            .account(linkedAccount)
            .transactionType(TRANSACTION_PAY)
            .amount(scale(amount))
            .transactionDatetime(OffsetDateTime.now())
            .status(TRANSACTION_STATUS_COMPLETED)
            .build();
    }

    private BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
