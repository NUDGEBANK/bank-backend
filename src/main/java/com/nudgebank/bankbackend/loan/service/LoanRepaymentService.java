package com.nudgebank.bankbackend.loan.service;

import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.card.domain.Market;
import com.nudgebank.bankbackend.card.domain.MarketCategory;
import com.nudgebank.bankbackend.card.repository.CardTransactionRepository;
import com.nudgebank.bankbackend.card.repository.MarketCategoryRepository;
import com.nudgebank.bankbackend.card.repository.MarketRepository;
import com.nudgebank.bankbackend.loan.domain.Loan;
import com.nudgebank.bankbackend.loan.domain.LoanApplication;
import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import com.nudgebank.bankbackend.loan.domain.LoanRepaymentHistory;
import com.nudgebank.bankbackend.loan.domain.RepaymentSchedule;
import com.nudgebank.bankbackend.loan.dto.LoanRepaymentExecuteResponse;
import com.nudgebank.bankbackend.loan.repository.LoanHistoryRepository;
import com.nudgebank.bankbackend.loan.repository.LoanRepaymentHistoryRepository;
import com.nudgebank.bankbackend.loan.repository.LoanRepository;
import com.nudgebank.bankbackend.loan.repository.RepaymentScheduleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LoanRepaymentService {

    private static final String SELF_DEVELOPMENT_TYPE = "SELF_DEVELOPMENT";
    private static final String CONSUMPTION_ANALYSIS_TYPE = "CONSUMPTION_ANALYSIS";
    private static final String YOUTH_LOAN_PRODUCT_KEY = "youth-loan";
    private static final String CONSUMPTION_LOAN_PRODUCT_KEY = "consumption-loan";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal OVERDUE_SPREAD = new BigDecimal("3.0");
    private static final BigDecimal MAX_OVERDUE_RATE = new BigDecimal("15.0");
    private static final String REPAYMENT_CATEGORY_NAME = "대출상환";
    private static final String REPAYMENT_MCC = "6012";
    private static final String REPAYMENT_MARKET_NAME = "NudgeBank 대출 상환";
    private static final String EQUAL_INSTALLMENT_TYPE = "EQUAL_INSTALLMENT";

    private final LoanRepository loanRepository;
    private final LoanHistoryRepository loanHistoryRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final LoanRepaymentHistoryRepository loanRepaymentHistoryRepository;
    private final AccountRepository accountRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final MarketCategoryRepository marketCategoryRepository;
    private final MarketRepository marketRepository;

    public LoanRepaymentExecuteResponse repay(Long memberId, String productKey, BigDecimal requestedAmount) {
        ResolvedLoan resolvedLoan = resolveLoan(memberId, productKey);
        refreshEqualInstallmentSchedulesIfNeeded(resolvedLoan.loanHistory(), resolvedLoan.loan());
        List<RepaymentSchedule> targetSchedules = resolveTargetSchedules(resolvedLoan.loanHistory());
        if (targetSchedules.isEmpty()) {
            throw new IllegalStateException("상환할 예정 회차가 없습니다.");
        }

        BigDecimal payableAmount = calculatePayableAmount(targetSchedules, resolvedLoan.loan(), true);
        BigDecimal repaymentAmount = normalizeRequestedAmount(requestedAmount, payableAmount);
        if (repaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("상환 금액은 0보다 커야 합니다.");
        }

        Account sourceAccount = accountRepository.findByIdForUpdate(resolvedLoan.loanHistory().getCard().getAccountId())
            .orElseThrow(() -> new EntityNotFoundException("상환 계좌를 찾을 수 없습니다."));
        sourceAccount.withdraw(repaymentAmount);

        AppliedRepayment appliedRepayment = applyRepayment(
            resolvedLoan.loanHistory(),
            resolvedLoan.loan(),
            targetSchedules,
            repaymentAmount,
            true
        );

        CardTransaction transaction = cardTransactionRepository.save(CardTransaction.builder()
            .card(resolvedLoan.loanHistory().getCard())
            .market(resolveRepaymentMarket())
            .qrId(generateRepaymentQrId())
            .category(resolveRepaymentCategory())
            .amount(appliedRepayment.totalPaid())
            .transactionDatetime(OffsetDateTime.now())
            .menuName(resolveManualRepaymentMenuName(resolvedLoan.loan()))
            .quantity(1)
            .build());

        loanRepaymentHistoryRepository.save(LoanRepaymentHistory.create(
            resolvedLoan.loanHistory(),
            transaction,
            appliedRepayment.totalPaid(),
            BigDecimal.ZERO,
            OffsetDateTime.now(),
            nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal())
        ));

        syncLoanHistoryStatus(resolvedLoan.loanHistory(), resolvedLoan.loan());

        return new LoanRepaymentExecuteResponse(
            appliedRepayment.totalPaid(),
            appliedRepayment.principalPaid(),
            appliedRepayment.interestPaid(),
            appliedRepayment.overdueInterestPaid(),
            nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
            resolvedLoan.loanHistory().getStatus(),
            false,
            "MANUAL_COMPLETED",
            null
        );
    }

    public LoanRepaymentExecuteResponse executeAutoRepayment(Long memberId, String productKey) {
        ResolvedLoan resolvedLoan = resolveSelfDevelopmentLoan(memberId, productKey);
        List<RepaymentSchedule> dueSchedules = repaymentScheduleRepository
            .findAllUnsettledByLoanHistoryIdForUpdate(resolvedLoan.loanHistory().getId()).stream()
            .filter(schedule -> schedule.getDueDate() != null && !schedule.getDueDate().isAfter(LocalDate.now()))
            .toList();

        if (dueSchedules.isEmpty()) {
            syncLoanHistoryStatus(resolvedLoan.loanHistory(), resolvedLoan.loan());
            return new LoanRepaymentExecuteResponse(
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
                resolvedLoan.loanHistory().getStatus(),
                false,
                "AUTO_SKIPPED",
                "NO_DUE_AMOUNT"
            );
        }

        BigDecimal dueAmount = calculatePayableAmount(dueSchedules, resolvedLoan.loan(), true);
        if (dueAmount.compareTo(BigDecimal.ZERO) <= 0) {
            syncLoanHistoryStatus(resolvedLoan.loanHistory(), resolvedLoan.loan());
            return new LoanRepaymentExecuteResponse(
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
                resolvedLoan.loanHistory().getStatus(),
                false,
                "AUTO_SKIPPED",
                "NO_DUE_AMOUNT"
            );
        }

        Account sourceAccount = accountRepository.findByIdForUpdate(resolvedLoan.loanHistory().getCard().getAccountId())
            .orElseThrow(() -> new EntityNotFoundException("상환 계좌를 찾을 수 없습니다."));

        if (availableBalance(sourceAccount).compareTo(dueAmount) < 0) {
            syncLoanHistoryStatus(resolvedLoan.loanHistory(), resolvedLoan.loan());
            return new LoanRepaymentExecuteResponse(
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
                resolvedLoan.loanHistory().getStatus(),
                false,
                "AUTO_FAILED",
                "INSUFFICIENT_BALANCE"
            );
        }

        sourceAccount.withdraw(dueAmount);
        AppliedRepayment appliedRepayment = applyRepayment(
            resolvedLoan.loanHistory(),
            resolvedLoan.loan(),
            dueSchedules,
            dueAmount,
            true
        );

        CardTransaction transaction = cardTransactionRepository.save(CardTransaction.builder()
            .card(resolvedLoan.loanHistory().getCard())
            .market(resolveRepaymentMarket())
            .qrId(generateRepaymentQrId())
            .category(resolveRepaymentCategory())
            .amount(appliedRepayment.totalPaid())
            .transactionDatetime(OffsetDateTime.now())
            .menuName("자기계발 대출 자동이체")
            .quantity(1)
            .build());

        loanRepaymentHistoryRepository.save(LoanRepaymentHistory.create(
            resolvedLoan.loanHistory(),
            transaction,
            appliedRepayment.totalPaid(),
            BigDecimal.ZERO,
            OffsetDateTime.now(),
            nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal())
        ));

        syncLoanHistoryStatus(resolvedLoan.loanHistory(), resolvedLoan.loan());

        return new LoanRepaymentExecuteResponse(
            appliedRepayment.totalPaid(),
            appliedRepayment.principalPaid(),
            appliedRepayment.interestPaid(),
            appliedRepayment.overdueInterestPaid(),
            nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
            resolvedLoan.loanHistory().getStatus(),
            true,
            "AUTO_COMPLETED",
            null
        );
    }

    private AppliedRepayment applyRepayment(
        LoanHistory loanHistory,
        Loan loan,
        List<RepaymentSchedule> schedules,
        BigDecimal requestedAmount,
        boolean applyOverdueInterest
    ) {
        BigDecimal remainingAmount = requestedAmount;
        BigDecimal paidPrincipal = ZERO;
        BigDecimal paidInterest = ZERO;
        BigDecimal paidOverdueInterest = ZERO;
        BigDecimal currentInterestRate = nullSafe(loan.getInterestRate());

        for (RepaymentSchedule schedule : schedules) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal unpaidInterest = schedule.getRemainingPlannedInterest();
            BigDecimal unpaidPrincipal = schedule.getRemainingPlannedPrincipal();
            int overdueDays = calculateOverdueDays(schedule);
            BigDecimal overdueInterest = calculateOverdueInterest(
                unpaidPrincipal.add(unpaidInterest),
                currentInterestRate,
                applyOverdueInterest ? overdueDays : 0
            );

            BigDecimal interestDue = unpaidInterest.add(overdueInterest);
            BigDecimal interestPayment = remainingAmount.min(interestDue);
            if (interestPayment.compareTo(BigDecimal.ZERO) > 0) {
                schedule.addPaidInterest(interestPayment);
                paidInterest = paidInterest.add(interestPayment);
                BigDecimal overdueInterestPayment = interestPayment.min(overdueInterest);
                paidOverdueInterest = paidOverdueInterest.add(overdueInterestPayment);
                remainingAmount = remainingAmount.subtract(interestPayment);
            }

            BigDecimal principalPayment = remainingAmount.min(unpaidPrincipal);
            if (principalPayment.compareTo(BigDecimal.ZERO) > 0) {
                schedule.addPaidPrincipal(principalPayment);
                paidPrincipal = paidPrincipal.add(principalPayment);
                remainingAmount = remainingAmount.subtract(principalPayment);
            }

            if (schedule.getRemainingPlannedPrincipal().compareTo(BigDecimal.ZERO) <= 0
                && schedule.getRemainingPlannedInterest().compareTo(BigDecimal.ZERO) <= 0
                && remainingAmount.compareTo(BigDecimal.ZERO) >= 0) {
                schedule.markSettled();
            } else {
                schedule.markPending(calculateOverdueDays(schedule));
            }
        }

        repaymentScheduleRepository.saveAll(schedules);
        if (paidPrincipal.compareTo(BigDecimal.ZERO) > 0) {
            loanHistory.applyRepayment(paidPrincipal);
        }

        return new AppliedRepayment(
            paidPrincipal.add(paidInterest),
            paidPrincipal,
            paidInterest,
            paidOverdueInterest
        );
    }

    private void syncLoanHistoryStatus(LoanHistory loanHistory, Loan loan) {
        List<RepaymentSchedule> allSchedules =
            repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());

        RepaymentSchedule nextSchedule = allSchedules.stream()
            .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()))
            .findFirst()
            .orElse(null);
        LocalDate expectedCompletionDate = allSchedules.stream()
            .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()))
            .map(RepaymentSchedule::getDueDate)
            .filter(java.util.Objects::nonNull)
            .reduce((first, second) -> second)
            .orElse(loanHistory.getEndDate());

        boolean hasOverdue = allSchedules.stream()
            .anyMatch(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()) && calculateOverdueDays(schedule) > 0);

        for (RepaymentSchedule schedule : allSchedules) {
            if (Boolean.TRUE.equals(schedule.getIsSettled())) {
                continue;
            }
            schedule.markPending(calculateOverdueDays(schedule));
        }

        repaymentScheduleRepository.saveAll(allSchedules);
        if (loan != null
            && loan.getLoanApplication() != null
            && loan.getLoanApplication().getLoanProduct() != null
            && CONSUMPTION_ANALYSIS_TYPE.equals(loan.getLoanApplication().getLoanProduct().getLoanProductType())) {
            loanHistory.syncRepaymentStatus(
                nextSchedule != null ? nextSchedule.getDueDate() : null,
                expectedCompletionDate,
                hasOverdue
            );
            return;
        }

        loanHistory.syncRepaymentStatus(nextSchedule != null ? nextSchedule.getDueDate() : null, hasOverdue);
    }

    private BigDecimal calculatePayableAmount(List<RepaymentSchedule> schedules, Loan loan, boolean applyOverdueInterest) {
        BigDecimal total = ZERO;
        for (RepaymentSchedule schedule : schedules) {
            BigDecimal unpaidPrincipal = schedule.getRemainingPlannedPrincipal();
            BigDecimal unpaidInterest = schedule.getRemainingPlannedInterest();
            BigDecimal overdueInterest = calculateOverdueInterest(
                unpaidPrincipal.add(unpaidInterest),
                nullSafe(loan.getInterestRate()),
                applyOverdueInterest ? calculateOverdueDays(schedule) : 0
            );
            total = total.add(unpaidPrincipal).add(unpaidInterest).add(overdueInterest);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private List<RepaymentSchedule> resolveTargetSchedules(LoanHistory loanHistory) {
        List<RepaymentSchedule> unsettledSchedules =
            repaymentScheduleRepository.findAllUnsettledByLoanHistoryIdForUpdate(loanHistory.getId());
        if (unsettledSchedules.isEmpty()) {
            return List.of();
        }

        List<RepaymentSchedule> dueSchedules = unsettledSchedules.stream()
            .filter(schedule -> schedule.getDueDate() != null && !schedule.getDueDate().isAfter(LocalDate.now()))
            .toList();

        if (!dueSchedules.isEmpty()) {
            return dueSchedules;
        }

        return List.of(unsettledSchedules.get(0));
    }

    private ResolvedLoan resolveSelfDevelopmentLoan(Long memberId, String productKey) {
        if (!YOUTH_LOAN_PRODUCT_KEY.equals(productKey)) {
            throw new IllegalArgumentException("자기계발 대출만 상환할 수 있습니다.");
        }

        return resolveLoanByProductType(memberId, SELF_DEVELOPMENT_TYPE);
    }

    private ResolvedLoan resolveLoan(Long memberId, String productKey) {
        return switch (productKey) {
            case YOUTH_LOAN_PRODUCT_KEY -> resolveLoanByProductType(memberId, SELF_DEVELOPMENT_TYPE);
            case CONSUMPTION_LOAN_PRODUCT_KEY -> resolveLoanByProductType(memberId, CONSUMPTION_ANALYSIS_TYPE);
            default -> throw new IllegalArgumentException("지원하지 않는 상품입니다. productKey=" + productKey);
        };
    }

    private ResolvedLoan resolveLoanByProductType(Long memberId, String loanProductType) {

        Loan loan = loanRepository
            .findTopByMember_MemberIdAndLoanApplication_LoanProduct_LoanProductTypeOrderByStartDateDescIdDesc(
                memberId,
                loanProductType
            )
            .orElseThrow(() -> new EntityNotFoundException("상환 대상 대출을 찾을 수 없습니다."));

        LoanApplication application = loan.getLoanApplication();
        if (application == null || application.getCard() == null) {
            throw new IllegalStateException("대출 상환용 카드 정보가 없습니다.");
        }

        LoanHistory loanHistory = loanHistoryRepository
            .findTopByMember_MemberIdAndCard_CardIdAndTotalPrincipalAndStartDateAndEndDateOrderByCreatedAtDesc(
                memberId,
                application.getCard().getCardId(),
                nullSafe(loan.getPrincipalAmount()),
                loan.getStartDate(),
                loan.getEndDate()
            )
            .orElseThrow(() -> new EntityNotFoundException("상환 대상 대출 이력을 찾을 수 없습니다."));

        return new ResolvedLoan(loan, loanHistory);
    }

    private void refreshEqualInstallmentSchedulesIfNeeded(LoanHistory loanHistory, Loan loan) {
        if (loan.getLoanApplication() == null
            || loan.getLoanApplication().getLoanProduct() == null
            || !CONSUMPTION_ANALYSIS_TYPE.equals(loan.getLoanApplication().getLoanProduct().getLoanProductType())
            || !EQUAL_INSTALLMENT_TYPE.equals(loan.getLoanApplication().getLoanProduct().getRepaymentType())) {
            return;
        }

        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());
        if (schedules.isEmpty()) {
            return;
        }

        List<RepaymentSchedule> unsettledSchedules = schedules.stream()
            .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()))
            .toList();
        if (unsettledSchedules.isEmpty()) {
            return;
        }

        BigDecimal monthlyPayment = calculateEqualInstallmentAmount(
            nullSafe(loanHistory.getTotalPrincipal()),
            nullSafe(loan.getInterestRate()),
            schedules.size()
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
            repaymentScheduleRepository.saveAll(schedules);
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

    private String resolveManualRepaymentMenuName(Loan loan) {
        if (loan != null
            && loan.getLoanApplication() != null
            && loan.getLoanApplication().getLoanProduct() != null
            && CONSUMPTION_ANALYSIS_TYPE.equals(loan.getLoanApplication().getLoanProduct().getLoanProductType())) {
            return "소비분석 대출 상환";
        }
        return "자기계발 대출 상환";
    }

    private BigDecimal normalizeRequestedAmount(BigDecimal requestedAmount, BigDecimal payableAmount) {
        if (requestedAmount == null) {
            return payableAmount;
        }

        BigDecimal normalized = requestedAmount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }

        return normalized.min(payableAmount);
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

    private BigDecimal availableBalance(Account account) {
        BigDecimal protectedBalance = account.getProtectedBalance() != null ? account.getProtectedBalance() : ZERO;
        return nullSafe(account.getBalance()).subtract(protectedBalance);
    }

    private MarketCategory resolveRepaymentCategory() {
        return marketCategoryRepository.findByCategoryName(REPAYMENT_CATEGORY_NAME)
            .orElseGet(() -> marketCategoryRepository.save(MarketCategory.create(REPAYMENT_CATEGORY_NAME, REPAYMENT_MCC)));
    }

    private Market resolveRepaymentMarket() {
        MarketCategory category = resolveRepaymentCategory();
        return marketRepository.findByMarketNameAndCategory_CategoryId(REPAYMENT_MARKET_NAME, category.getCategoryId())
            .orElseGet(() -> marketRepository.save(Market.create(category, REPAYMENT_MARKET_NAME)));
    }

    private String generateRepaymentQrId() {
        String candidate = "loan-repayment-" + UUID.randomUUID();
        while (cardTransactionRepository.existsByQrId(candidate)) {
            candidate = "loan-repayment-" + UUID.randomUUID();
        }
        return candidate;
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    private record ResolvedLoan(Loan loan, LoanHistory loanHistory) {}

    private record AppliedRepayment(
        BigDecimal totalPaid,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal overdueInterestPaid
    ) {}
}
