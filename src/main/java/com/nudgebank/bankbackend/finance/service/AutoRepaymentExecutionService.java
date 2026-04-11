package com.nudgebank.bankbackend.finance.service;

import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.finance.dto.AutoRepaymentDecisionResponse;
import com.nudgebank.bankbackend.loan.domain.Loan;
import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import com.nudgebank.bankbackend.loan.domain.LoanRepaymentHistory;
import com.nudgebank.bankbackend.loan.domain.RepaymentSchedule;
import com.nudgebank.bankbackend.loan.repository.LoanHistoryRepository;
import com.nudgebank.bankbackend.loan.repository.LoanRepaymentHistoryRepository;
import com.nudgebank.bankbackend.loan.repository.LoanRepository;
import com.nudgebank.bankbackend.loan.repository.RepaymentScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AutoRepaymentExecutionService {

    private static final String CONSUMPTION_ANALYSIS_TYPE = "CONSUMPTION_ANALYSIS";
    private static final String EQUAL_INSTALLMENT_TYPE = "EQUAL_INSTALLMENT";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal OVERDUE_SPREAD = new BigDecimal("3.0");
    private static final BigDecimal MAX_OVERDUE_RATE = new BigDecimal("15.0");

    private final AutoRepaymentPolicyService autoRepaymentPolicyService;
    private final LoanRepository loanRepository;
    private final LoanHistoryRepository loanHistoryRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final LoanRepaymentHistoryRepository loanRepaymentHistoryRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public AutoRepaymentExecutionResult executeAfterPayment(Long memberId, CardTransaction transaction) {
        ResolvedConsumptionLoan resolvedLoan = resolveConsumptionAnalysisLoan(memberId, transaction);
        if (resolvedLoan == null) {
            return AutoRepaymentExecutionResult.notApplied();
        }

        List<RepaymentSchedule> unsettledSchedules = repaymentScheduleRepository
                .findAllUnsettledByLoanHistoryIdForUpdate(resolvedLoan.loanHistory().getId());
        refreshEqualInstallmentSchedulesIfNeeded(resolvedLoan.loanHistory(), resolvedLoan.loan(), unsettledSchedules);
        List<RepaymentSchedule> schedules = unsettledSchedules.stream()
                .filter(schedule -> schedule.getDueDate() != null && !schedule.getDueDate().isAfter(LocalDate.now()))
                .toList();
        if (schedules.isEmpty()) {
            syncLoanHistory(resolvedLoan.loanHistory(), unsettledSchedules);
            return AutoRepaymentExecutionResult.notApplied();
        }

        AutoRepaymentDecisionResponse decision = autoRepaymentPolicyService.decideAutoRepayment(
                memberId,
                transaction.getTransactionId()
        );

        BigDecimal ratio = nullSafe(decision.getFinalRepaymentRatio());
        if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
            return AutoRepaymentExecutionResult.fromDecision(
                    decision,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
                    false
            );
        }

        Account account = accountRepository.findByIdForUpdate(transaction.getCard().getAccountId())
                .orElseThrow(() -> new IllegalStateException(
                        "자동상환용 계좌를 찾을 수 없습니다. accountId=" + transaction.getCard().getAccountId()
                ));

        BigDecimal repaymentAmount = calculateRepaymentAmount(
                transaction.getAmount(),
                ratio,
                account,
                schedules,
                resolvedLoan.loan()
        );
        if (repaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return AutoRepaymentExecutionResult.fromDecision(
                    decision,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
                    false
            );
        }

        account.withdraw(repaymentAmount);
        AppliedRepayment appliedRepayment = applyRepaymentToSchedules(
                resolvedLoan.loanHistory(),
                resolvedLoan.loan(),
                schedules,
                repaymentAmount
        );
        syncLoanHistory(resolvedLoan.loanHistory(), unsettledSchedules);

        loanRepaymentHistoryRepository.save(LoanRepaymentHistory.create(
                resolvedLoan.loanHistory(),
                transaction,
                appliedRepayment.totalPaid(),
                toPercent(ratio),
                OffsetDateTime.now(),
                resolvedLoan.loanHistory().getRemainingPrincipal()
        ));

        return AutoRepaymentExecutionResult.fromDecision(
                decision,
                appliedRepayment.totalPaid(),
                nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
                appliedRepayment.totalPaid().compareTo(BigDecimal.ZERO) > 0
        );
    }

    private BigDecimal calculateRepaymentAmount(
            BigDecimal transactionAmount,
            BigDecimal ratio,
            Account account,
            List<RepaymentSchedule> schedules,
            Loan loan
    ) {
        BigDecimal requestedAmount = nullSafe(transactionAmount)
                .multiply(ratio)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal outstandingAmount = schedules.stream()
                .map(schedule -> {
                    BigDecimal overdueInterest = calculateOverdueInterest(
                            schedule.getRemainingPlannedPrincipal().add(schedule.getRemainingPlannedInterest()),
                            nullSafe(loan.getInterestRate()),
                            calculateOverdueDays(schedule)
                    );
                    return schedule.getRemainingPlannedPrincipal()
                            .add(schedule.getRemainingPlannedInterest())
                            .add(overdueInterest);
                })
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal availableBalance = nullSafe(account.getBalance()).subtract(nullSafe(account.getProtectedBalance()));
        return requestedAmount.min(outstandingAmount).min(availableBalance).max(ZERO);
    }

    private AppliedRepayment applyRepaymentToSchedules(
            LoanHistory loanHistory,
            Loan loan,
            List<RepaymentSchedule> schedules,
            BigDecimal requestedAmount
    ) {
        BigDecimal remainingAmount = requestedAmount;
        BigDecimal paidPrincipal = ZERO;
        BigDecimal paidInterest = ZERO;
        BigDecimal paidOverdueInterest = ZERO;

        for (RepaymentSchedule schedule : schedules) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal overdueInterest = calculateOverdueInterest(
                    schedule.getRemainingPlannedPrincipal().add(schedule.getRemainingPlannedInterest()),
                    nullSafe(loan.getInterestRate()),
                    calculateOverdueDays(schedule)
            );
            BigDecimal interestDue = schedule.getRemainingPlannedInterest().add(overdueInterest);
            BigDecimal interestPayment = remainingAmount.min(interestDue);
            if (interestPayment.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal plannedInterestPayment = interestPayment.min(schedule.getRemainingPlannedInterest());
                BigDecimal overdueInterestPayment = interestPayment.subtract(plannedInterestPayment).max(BigDecimal.ZERO);
                if (plannedInterestPayment.compareTo(BigDecimal.ZERO) > 0) {
                    schedule.addPaidInterest(plannedInterestPayment);
                    paidInterest = paidInterest.add(plannedInterestPayment);
                }
                paidOverdueInterest = paidOverdueInterest.add(overdueInterestPayment);
                remainingAmount = remainingAmount.subtract(interestPayment);
            }

            BigDecimal principalPayment = remainingAmount.min(schedule.getRemainingPlannedPrincipal());
            if (principalPayment.compareTo(BigDecimal.ZERO) > 0) {
                schedule.addPaidPrincipal(principalPayment);
                paidPrincipal = paidPrincipal.add(principalPayment);
                remainingAmount = remainingAmount.subtract(principalPayment);
            }

            if (schedule.getRemainingPlannedPrincipal().compareTo(BigDecimal.ZERO) <= 0
                    && schedule.getRemainingPlannedInterest().compareTo(BigDecimal.ZERO) <= 0) {
                schedule.markSettled();
            } else {
                schedule.markPending(calculateOverdueDays(schedule));
            }
        }

        repaymentScheduleRepository.saveAll(schedules);
        loanHistory.synchronizeRemainingPrincipal(sumRemainingPrincipal(schedules));

        return new AppliedRepayment(
                paidPrincipal.add(paidInterest).add(paidOverdueInterest),
                paidPrincipal,
                paidInterest,
                paidOverdueInterest
        );
    }

    private void syncLoanHistory(LoanHistory loanHistory, List<RepaymentSchedule> schedules) {
        RepaymentSchedule nextSchedule = schedules.stream()
                .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()))
                .findFirst()
                .orElse(null);

        boolean hasOverdue = schedules.stream()
                .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()))
                .peek(schedule -> schedule.markPending(calculateOverdueDays(schedule)))
                .anyMatch(schedule -> (schedule.getOverdueDays() != null ? schedule.getOverdueDays() : 0) > 0);

        repaymentScheduleRepository.saveAll(schedules);
        loanHistory.synchronizeRemainingPrincipal(sumRemainingPrincipal(schedules));
        loanHistory.syncRepaymentStatus(nextSchedule != null ? nextSchedule.getDueDate() : null, hasOverdue);
    }

    private int calculateOverdueDays(RepaymentSchedule schedule) {
        if (schedule.getDueDate() == null) {
            return 0;
        }

        if (Boolean.TRUE.equals(schedule.getIsSettled())) {
            return schedule.getOverdueDays() != null ? schedule.getOverdueDays() : 0;
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
                .divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);
    }

    private ResolvedConsumptionLoan resolveConsumptionAnalysisLoan(Long memberId, CardTransaction transaction) {
        Loan loan = loanRepository
                .findTopByMember_MemberIdAndLoanApplication_LoanProduct_LoanProductTypeOrderByStartDateDescIdDesc(
                        memberId,
                        CONSUMPTION_ANALYSIS_TYPE
                )
                .orElse(null);

        if (loan == null || loan.getLoanApplication() == null || loan.getLoanApplication().getCard() == null) {
            return null;
        }

        if (!loan.getLoanApplication().getCard().getCardId().equals(transaction.getCard().getCardId())) {
            return null;
        }

        LoanHistory loanHistory = loanHistoryRepository
                .findTopByMember_MemberIdAndCard_CardIdAndTotalPrincipalAndStartDateOrderByCreatedAtDesc(
                        memberId,
                        transaction.getCard().getCardId(),
                        nullSafe(loan.getPrincipalAmount()),
                        loan.getStartDate()
                )
                .orElse(null);

        if (loanHistory == null) {
            return null;
        }

        return new ResolvedConsumptionLoan(loan, loanHistory);
    }

    private BigDecimal toPercent(BigDecimal ratio) {
        return nullSafe(ratio)
                .multiply(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private void refreshEqualInstallmentSchedulesIfNeeded(
            LoanHistory loanHistory,
            Loan loan,
            List<RepaymentSchedule> unsettledSchedules
    ) {
        if (loan.getLoanApplication() == null
                || loan.getLoanApplication().getLoanProduct() == null
                || !CONSUMPTION_ANALYSIS_TYPE.equals(loan.getLoanApplication().getLoanProduct().getLoanProductType())
                || !EQUAL_INSTALLMENT_TYPE.equals(loan.getLoanApplication().getLoanProduct().getRepaymentType())
                || unsettledSchedules.isEmpty()) {
            return;
        }

        List<RepaymentSchedule> allSchedules = repaymentScheduleRepository
                .findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());
        BigDecimal monthlyPayment = calculateEqualInstallmentAmount(
                nullSafe(loanHistory.getTotalPrincipal()),
                nullSafe(loan.getInterestRate()),
                allSchedules.size()
        );
        BigDecimal remainingPrincipal = nullSafe(loanHistory.getRemainingPrincipal());
        boolean changed = false;

        for (int index = 0; index < unsettledSchedules.size(); index++) {
            RepaymentSchedule schedule = unsettledSchedules.get(index);
            BigDecimal plannedInterest = remainingPrincipal
                    .multiply(nullSafe(loan.getInterestRate()))
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            BigDecimal plannedPrincipal = index == unsettledSchedules.size() - 1
                    ? remainingPrincipal
                    : monthlyPayment.subtract(plannedInterest).max(BigDecimal.ZERO);

            if (nullSafe(schedule.getPlannedPrincipal()).compareTo(plannedPrincipal) != 0
                    || nullSafe(schedule.getPlannedInterest()).compareTo(plannedInterest) != 0) {
                schedule.updatePlannedAmounts(plannedPrincipal, plannedInterest);
                changed = true;
            }

            remainingPrincipal = remainingPrincipal.subtract(plannedPrincipal).max(BigDecimal.ZERO);
        }

        if (changed) {
            repaymentScheduleRepository.saveAll(unsettledSchedules);
        }
    }

    private BigDecimal calculateEqualInstallmentAmount(
            BigDecimal principalAmount,
            BigDecimal annualInterestRate,
            int repaymentMonths
    ) {
        if (repaymentMonths <= 0) {
            return principalAmount;
        }
        if (principalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }

        BigDecimal monthlyRate = annualInterestRate
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principalAmount.divide(BigDecimal.valueOf(repaymentMonths), 2, RoundingMode.HALF_UP);
        }

        BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(repaymentMonths);
        BigDecimal numerator = principalAmount.multiply(monthlyRate).multiply(factor);
        BigDecimal denominator = factor.subtract(BigDecimal.ONE);

        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumRemainingPrincipal(List<RepaymentSchedule> schedules) {
        return schedules.stream()
                .map(RepaymentSchedule::getRemainingPlannedPrincipal)
                .map(this::nullSafe)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private record ResolvedConsumptionLoan(
            Loan loan,
            LoanHistory loanHistory
    ) {}

    private record AppliedRepayment(
            BigDecimal totalPaid,
            BigDecimal principalPaid,
            BigDecimal interestPaid,
            BigDecimal overdueInterestPaid
    ) {}

    public record AutoRepaymentExecutionResult(
            boolean autoRepaymentApplied,
            String repaymentAction,
            String policyGrade,
            BigDecimal repaymentRate,
            BigDecimal repaymentAmount,
            BigDecimal remainingLoanBalance
    ) {
        public static AutoRepaymentExecutionResult notApplied() {
            return new AutoRepaymentExecutionResult(
                    false,
                    null,
                    null,
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    null
            );
        }

        public static AutoRepaymentExecutionResult failed() {
            return new AutoRepaymentExecutionResult(
                    false,
                    "HOLD",
                    "FAILED",
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    null
            );
        }

        public static AutoRepaymentExecutionResult fromDecision(
                AutoRepaymentDecisionResponse decision,
                BigDecimal repaymentAmount,
                BigDecimal remainingLoanBalance,
                boolean applied
        ) {
            BigDecimal repaymentRate = decision.getFinalRepaymentRatio() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : decision.getFinalRepaymentRatio()
                    .multiply(ONE_HUNDRED)
                    .setScale(2, RoundingMode.HALF_UP);

            return new AutoRepaymentExecutionResult(
                    applied,
                    decision.getRepaymentAction(),
                    decision.getPolicyGrade(),
                    repaymentRate,
                    repaymentAmount,
                    remainingLoanBalance
            );
        }
    }
}
