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
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal OVERDUE_SPREAD = new BigDecimal("3.0");
    private static final BigDecimal MAX_OVERDUE_RATE = new BigDecimal("15.0");
    private static final String REPAYMENT_CATEGORY_NAME = "대출상환";
    private static final String REPAYMENT_MCC = "6012";
    private static final String REPAYMENT_MARKET_NAME = "NudgeBank 대출 상환";

    private final LoanRepository loanRepository;
    private final LoanHistoryRepository loanHistoryRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final LoanRepaymentHistoryRepository loanRepaymentHistoryRepository;
    private final AccountRepository accountRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final MarketCategoryRepository marketCategoryRepository;
    private final MarketRepository marketRepository;

    public LoanRepaymentExecuteResponse repay(Long memberId, String productKey, BigDecimal requestedAmount) {
        ResolvedLoan resolvedLoan = resolveSelfDevelopmentLoan(memberId, productKey);
        List<RepaymentSchedule> targetSchedules = resolveTargetSchedules(resolvedLoan.loanHistory());
        if (targetSchedules.isEmpty()) {
            throw new IllegalStateException("상환할 예정 회차가 없습니다.");
        }

        BigDecimal payableAmount = calculatePayableAmount(targetSchedules, resolvedLoan.loan());
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
            repaymentAmount
        );

        CardTransaction transaction = cardTransactionRepository.save(CardTransaction.builder()
            .card(resolvedLoan.loanHistory().getCard())
            .market(resolveRepaymentMarket())
            .qrId(generateRepaymentQrId())
            .category(resolveRepaymentCategory())
            .amount(appliedRepayment.totalPaid())
            .transactionDatetime(OffsetDateTime.now())
            .menuName("자기계발 대출 상환")
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

        syncLoanHistoryStatus(resolvedLoan.loanHistory());

        return new LoanRepaymentExecuteResponse(
            appliedRepayment.totalPaid(),
            appliedRepayment.principalPaid(),
            appliedRepayment.interestPaid(),
            appliedRepayment.overdueInterestPaid(),
            nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
            resolvedLoan.loanHistory().getStatus(),
            false
        );
    }

    public LoanRepaymentExecuteResponse executeAutoRepayment(Long memberId, String productKey) {
        ResolvedLoan resolvedLoan = resolveSelfDevelopmentLoan(memberId, productKey);
        List<RepaymentSchedule> dueSchedules = repaymentScheduleRepository
            .findAllByLoanHistory_IdAndIsSettledFalseOrderByDueDateAsc(resolvedLoan.loanHistory().getId()).stream()
            .filter(schedule -> schedule.getDueDate() != null && !schedule.getDueDate().isAfter(LocalDate.now()))
            .toList();

        if (dueSchedules.isEmpty()) {
            syncLoanHistoryStatus(resolvedLoan.loanHistory());
            return new LoanRepaymentExecuteResponse(
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
                resolvedLoan.loanHistory().getStatus(),
                true
            );
        }

        BigDecimal dueAmount = calculatePayableAmount(dueSchedules, resolvedLoan.loan());
        if (dueAmount.compareTo(BigDecimal.ZERO) <= 0) {
            syncLoanHistoryStatus(resolvedLoan.loanHistory());
            return new LoanRepaymentExecuteResponse(
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
                resolvedLoan.loanHistory().getStatus(),
                true
            );
        }

        Account sourceAccount = accountRepository.findByIdForUpdate(resolvedLoan.loanHistory().getCard().getAccountId())
            .orElseThrow(() -> new EntityNotFoundException("상환 계좌를 찾을 수 없습니다."));

        if (availableBalance(sourceAccount).compareTo(dueAmount) < 0) {
            syncLoanHistoryStatus(resolvedLoan.loanHistory());
            return new LoanRepaymentExecuteResponse(
                ZERO,
                ZERO,
                ZERO,
                ZERO,
                nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
                resolvedLoan.loanHistory().getStatus(),
                true
            );
        }

        sourceAccount.withdraw(dueAmount);
        AppliedRepayment appliedRepayment = applyRepayment(
            resolvedLoan.loanHistory(),
            resolvedLoan.loan(),
            dueSchedules,
            dueAmount
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

        syncLoanHistoryStatus(resolvedLoan.loanHistory());

        return new LoanRepaymentExecuteResponse(
            appliedRepayment.totalPaid(),
            appliedRepayment.principalPaid(),
            appliedRepayment.interestPaid(),
            appliedRepayment.overdueInterestPaid(),
            nullSafe(resolvedLoan.loanHistory().getRemainingPrincipal()),
            resolvedLoan.loanHistory().getStatus(),
            true
        );
    }

    private AppliedRepayment applyRepayment(
        LoanHistory loanHistory,
        Loan loan,
        List<RepaymentSchedule> schedules,
        BigDecimal requestedAmount
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
                overdueDays
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

    private void syncLoanHistoryStatus(LoanHistory loanHistory) {
        List<RepaymentSchedule> allSchedules =
            repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());

        RepaymentSchedule nextSchedule = allSchedules.stream()
            .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()))
            .findFirst()
            .orElse(null);

        boolean hasOverdue = allSchedules.stream()
            .anyMatch(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()) && calculateOverdueDays(schedule) > 0);

        for (RepaymentSchedule schedule : allSchedules) {
            if (Boolean.TRUE.equals(schedule.getIsSettled())) {
                continue;
            }
            schedule.markPending(calculateOverdueDays(schedule));
        }

        repaymentScheduleRepository.saveAll(allSchedules);
        loanHistory.syncRepaymentStatus(nextSchedule != null ? nextSchedule.getDueDate() : null, hasOverdue);
    }

    private BigDecimal calculatePayableAmount(List<RepaymentSchedule> schedules, Loan loan) {
        BigDecimal total = ZERO;
        for (RepaymentSchedule schedule : schedules) {
            BigDecimal unpaidPrincipal = schedule.getRemainingPlannedPrincipal();
            BigDecimal unpaidInterest = schedule.getRemainingPlannedInterest();
            BigDecimal overdueInterest = calculateOverdueInterest(
                unpaidPrincipal.add(unpaidInterest),
                nullSafe(loan.getInterestRate()),
                calculateOverdueDays(schedule)
            );
            total = total.add(unpaidPrincipal).add(unpaidInterest).add(overdueInterest);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private List<RepaymentSchedule> resolveTargetSchedules(LoanHistory loanHistory) {
        List<RepaymentSchedule> unsettledSchedules =
            repaymentScheduleRepository.findAllByLoanHistory_IdAndIsSettledFalseOrderByDueDateAsc(loanHistory.getId());
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
        if (!"youth-loan".equals(productKey)) {
            throw new IllegalArgumentException("자기계발 대출만 상환할 수 있습니다.");
        }

        Loan loan = loanRepository
            .findTopByMember_MemberIdAndLoanApplication_LoanProduct_LoanProductTypeOrderByStartDateDescIdDesc(
                memberId,
                SELF_DEVELOPMENT_TYPE
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
        if (schedule.getDueDate() == null || Boolean.TRUE.equals(schedule.getIsSettled())) {
            return 0;
        }

        return (int) Math.max(0, ChronoUnit.DAYS.between(schedule.getDueDate(), LocalDate.now()));
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
