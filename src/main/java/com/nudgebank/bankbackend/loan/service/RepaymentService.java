package com.nudgebank.bankbackend.loan.service;

import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.common.util.WonAmount;
import com.nudgebank.bankbackend.loan.domain.Loan;
import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import com.nudgebank.bankbackend.loan.domain.LoanRepaymentHistory;
import com.nudgebank.bankbackend.loan.domain.RepaymentSchedule;
import com.nudgebank.bankbackend.loan.repository.LoanRepaymentHistoryRepository;
import com.nudgebank.bankbackend.loan.repository.RepaymentScheduleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RepaymentService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal OVERDUE_SPREAD = new BigDecimal("3.0");
    private static final BigDecimal MAX_OVERDUE_RATE = new BigDecimal("15.0");

    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final LoanRepaymentHistoryRepository loanRepaymentHistoryRepository;

    public RepaymentResult repayWithOverpaymentBlocked(
            LoanHistory loanHistory,
            Loan loan,
            BigDecimal requestedAmount
    ) {
        validateRepaymentRequest(loanHistory, loan, requestedAmount);

        BigDecimal normalizedRequestedAmount = won(requestedAmount);
        BigDecimal payableAmount = calculatePayableAmount(loanHistory, loan);
        if (normalizedRequestedAmount.compareTo(payableAmount) > 0) {
            throw new IllegalArgumentException("상환 가능 금액을 초과했습니다.");
        }

        RepaymentResult result = applyRepayment(loanHistory, loan, normalizedRequestedAmount);
        saveRepaymentHistoryIfApplied(loanHistory, null, null, result);
        return result;
    }

    public RepaymentResult repay(LoanHistory loanHistory, Loan loan, BigDecimal requestedAmount, CardTransaction transaction) {
        RepaymentResult result = applyRepayment(loanHistory, loan, requestedAmount);
        saveRepaymentHistoryIfApplied(loanHistory, transaction, null, result);
        return result;
    }

    private RepaymentResult applyRepayment(LoanHistory loanHistory, Loan loan, BigDecimal requestedAmount) {
        validateRepaymentRequest(loanHistory, loan, requestedAmount);

        List<RepaymentSchedule> unsettledSchedules =
                repaymentScheduleRepository.findAllUnsettledByLoanHistoryIdForUpdate(loanHistory.getId());
        if (unsettledSchedules.isEmpty()) {
            syncLoanHistory(loanHistory);
            return RepaymentResult.notApplied(nullSafe(loanHistory.getRemainingPrincipal()), requestedAmount);
        }
        List<RepaymentSchedule> allSchedules =
                repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());
        List<RepaymentSchedule> targetSchedules = resolveRepaymentTargetSchedules(allSchedules, unsettledSchedules);
        if (targetSchedules.isEmpty()) {
            syncLoanHistory(loanHistory);
            return RepaymentResult.notApplied(nullSafe(loanHistory.getRemainingPrincipal()), requestedAmount);
        }

        BigDecimal remainingAmount = won(requestedAmount);
        BigDecimal paidPrincipal = ZERO;
        BigDecimal paidInterest = ZERO;
        BigDecimal paidOverdueInterest = ZERO;
        int settledScheduleCount = 0;

        for (RepaymentSchedule schedule : targetSchedules) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            updateScheduleOverdue(schedule, loan);

            BigDecimal overdueInterestPayment = remainingAmount.min(schedule.getRemainingPlannedOverdueInterest());
            if (overdueInterestPayment.compareTo(BigDecimal.ZERO) > 0) {
                schedule.addPaidOverdueInterest(overdueInterestPayment);
                paidOverdueInterest = paidOverdueInterest.add(overdueInterestPayment);
                remainingAmount = remainingAmount.subtract(overdueInterestPayment);
            }

            BigDecimal interestPayment = remainingAmount.min(schedule.getRemainingPlannedInterest());
            if (interestPayment.compareTo(BigDecimal.ZERO) > 0) {
                schedule.addPaidInterest(interestPayment);
                paidInterest = paidInterest.add(interestPayment);
                remainingAmount = remainingAmount.subtract(interestPayment);
            }

            BigDecimal principalPayment = remainingAmount.min(schedule.getRemainingPlannedPrincipal());
            if (principalPayment.compareTo(BigDecimal.ZERO) > 0) {
                schedule.addPaidPrincipal(principalPayment);
                paidPrincipal = paidPrincipal.add(principalPayment);
                remainingAmount = remainingAmount.subtract(principalPayment);
            }

            if (isFullyPaid(schedule)) {
                schedule.markSettled();
                settledScheduleCount++;
            } else {
                schedule.markPending(calculateOverdueDays(schedule));
            }
        }

        repaymentScheduleRepository.saveAll(targetSchedules);
        BigDecimal remainingPrincipal = syncLoanHistory(loanHistory);
        BigDecimal totalPaid = won(paidPrincipal.add(paidInterest).add(paidOverdueInterest));

        return new RepaymentResult(
                totalPaid.compareTo(BigDecimal.ZERO) > 0,
                won(requestedAmount),
                totalPaid,
                won(paidPrincipal),
                won(paidInterest),
                won(paidOverdueInterest),
                won(remainingAmount),
                won(remainingPrincipal),
                loanHistory.getStatus(),
                settledScheduleCount
        );
    }

    public RepaymentResult repay(
            LoanHistory loanHistory,
            Loan loan,
            BigDecimal requestedAmount,
            CardTransaction transaction,
            BigDecimal repaymentRate
    ) {
        RepaymentResult result = applyRepayment(loanHistory, loan, requestedAmount);
        saveRepaymentHistoryIfApplied(loanHistory, transaction, repaymentRate, result);
        return result;
    }

    private void validateRepaymentRequest(LoanHistory loanHistory, Loan loan, BigDecimal requestedAmount) {
        if (loanHistory == null) {
            throw new IllegalArgumentException("loanHistory is required.");
        }
        if (loan == null) {
            throw new IllegalArgumentException("loan is required.");
        }
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("repayment amount must be greater than zero.");
        }
    }

    private void saveRepaymentHistoryIfApplied(
            LoanHistory loanHistory,
            CardTransaction transaction,
            BigDecimal repaymentRate,
            RepaymentResult result
    ) {
        if (result == null || !result.applied()) {
            return;
        }

        repaymentRate = result.totalPaid().divide(transaction.getAmount(), 4, RoundingMode.HALF_UP);

        loanRepaymentHistoryRepository.save(LoanRepaymentHistory.create(
                loanHistory,
                transaction,
                won(result.totalPaid()),
                repaymentRate,
                OffsetDateTime.now(),
                won(result.remainingPrincipal())
        ));
    }

    public BigDecimal calculatePayableAmount(LoanHistory loanHistory, Loan loan) {
        if (loanHistory == null) {
            throw new IllegalArgumentException("loanHistory is required.");
        }
        if (loan == null) {
            throw new IllegalArgumentException("loan is required.");
        }

        List<RepaymentSchedule> unsettledSchedules =
                repaymentScheduleRepository.findAllUnsettledByLoanHistoryIdForUpdate(loanHistory.getId());
        List<RepaymentSchedule> allSchedules =
                repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());
        List<RepaymentSchedule> targetSchedules = resolveRepaymentTargetSchedules(allSchedules, unsettledSchedules);

        return targetSchedules.stream()
                .map(schedule -> {
                    updateScheduleOverdue(schedule, loan);
                    return schedule.getRemainingPlannedOverdueInterest()
                            .add(schedule.getRemainingPlannedInterest())
                            .add(schedule.getRemainingPlannedPrincipal());
                })
                .reduce(ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.DOWN);
    }

    private List<RepaymentSchedule> resolveRepaymentTargetSchedules(
            List<RepaymentSchedule> allSchedules,
            List<RepaymentSchedule> unsettledSchedules
    ) {
        if (allSchedules == null || allSchedules.isEmpty() || unsettledSchedules == null || unsettledSchedules.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now();
        LocalDate currentInstallmentBoundary = today.plusMonths(1);

        RepaymentSchedule currentSchedule = allSchedules.stream()
                .filter(schedule -> schedule.getDueDate() != null
                        && !schedule.getDueDate().isBefore(today)
                        && !schedule.getDueDate().isAfter(currentInstallmentBoundary))
                .findFirst()
                .orElse(null);

        if (currentSchedule == null || Boolean.TRUE.equals(currentSchedule.getIsSettled())) {
            return List.of();
        }

        LocalDate currentDueDate = currentSchedule.getDueDate();
        return unsettledSchedules.stream()
                .filter(schedule -> schedule.getDueDate() != null
                        && !schedule.getDueDate().isAfter(currentDueDate))
                .toList();
    }

    private void updateScheduleOverdue(RepaymentSchedule schedule, Loan loan) {
        int overdueDays = calculateOverdueDays(schedule);
        BigDecimal plannedOverdueInterest = calculateOverdueInterest(
                schedule.getRemainingPlannedPrincipal().add(schedule.getRemainingPlannedInterest()),
                nullSafe(loan.getInterestRate()),
                overdueDays
        );
        schedule.updatePlannedOverdueInterest(plannedOverdueInterest);
        schedule.markPending(overdueDays);
    }

    private boolean isFullyPaid(RepaymentSchedule schedule) {
        return schedule.getRemainingPlannedOverdueInterest().compareTo(BigDecimal.ZERO) <= 0
                && schedule.getRemainingPlannedInterest().compareTo(BigDecimal.ZERO) <= 0
                && schedule.getRemainingPlannedPrincipal().compareTo(BigDecimal.ZERO) <= 0;
    }

    private BigDecimal syncLoanHistory(LoanHistory loanHistory) {
        List<RepaymentSchedule> allSchedules =
                repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());

        RepaymentSchedule nextSchedule = allSchedules.stream()
                .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()))
                .findFirst()
                .orElse(null);

        boolean hasOverdue = false;
        for (RepaymentSchedule schedule : allSchedules) {
            if (Boolean.TRUE.equals(schedule.getIsSettled())) {
                continue;
            }
            int overdueDays = calculateOverdueDays(schedule);
            schedule.markPending(overdueDays);
            if (overdueDays > 0) {
                hasOverdue = true;
            }
        }

        repaymentScheduleRepository.saveAll(allSchedules);
        BigDecimal remainingPrincipal = sumRemainingPrincipal(allSchedules);
        loanHistory.synchronizeRemainingPrincipal(remainingPrincipal);
        loanHistory.syncRepaymentStatus(nextSchedule != null ? nextSchedule.getDueDate() : null, hasOverdue);
        return won(loanHistory.getRemainingPrincipal());
    }

    private int calculateOverdueDays(RepaymentSchedule schedule) {
        if (schedule.getDueDate() == null || Boolean.TRUE.equals(schedule.getIsSettled())) {
            return 0;
        }
        int existing = schedule.getOverdueDays() != null ? schedule.getOverdueDays() : 0;
        int calculated = (int) Math.max(0, ChronoUnit.DAYS.between(schedule.getDueDate(), LocalDate.now()));
        return Math.max(existing, calculated);
    }

    private BigDecimal calculateOverdueInterest(BigDecimal baseAmount, BigDecimal currentRate, int overdueDays) {
        if (overdueDays <= 0 || baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }

        BigDecimal overdueRate = currentRate.add(OVERDUE_SPREAD).min(MAX_OVERDUE_RATE);
        return baseAmount
                .multiply(overdueRate)
                .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(overdueDays))
                .divide(BigDecimal.valueOf(365), 10, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.DOWN);
    }

    private BigDecimal sumRemainingPrincipal(List<RepaymentSchedule> schedules) {
        return schedules.stream()
                .map(RepaymentSchedule::getRemainingPlannedPrincipal)
                .map(this::nullSafe)
                .reduce(ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.DOWN);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    public record RepaymentResult(
            boolean applied,
            BigDecimal requestedAmount,
            BigDecimal totalPaid,
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal overdueInterestPaid,
            BigDecimal unappliedAmount,
            BigDecimal remainingPrincipal,
            String loanStatus,
            int settledScheduleCount
    ) {
        public static RepaymentResult notApplied(BigDecimal remainingPrincipal, BigDecimal requestedAmount) {
            return new RepaymentResult(
                    false,
                    won(requestedAmount),
                    ZERO,
                    ZERO,
                    ZERO,
                    ZERO,
                    won(requestedAmount),
                    won(remainingPrincipal),
                    null,
                    0
            );
        }
    }

    private static BigDecimal won(BigDecimal value) {
        return WonAmount.floor(value);
    }
}
