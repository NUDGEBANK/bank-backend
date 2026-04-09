package com.nudgebank.bankbackend.finance.service;

import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.finance.dto.AutoRepaymentDecisionResponse;
import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import com.nudgebank.bankbackend.loan.domain.LoanRepaymentHistory;
import com.nudgebank.bankbackend.loan.repository.LoanHistoryRepository;
import com.nudgebank.bankbackend.loan.repository.LoanRepaymentHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AutoRepaymentExecutionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final AutoRepaymentPolicyService autoRepaymentPolicyService;
    private final LoanHistoryRepository loanHistoryRepository;
    private final LoanRepaymentHistoryRepository loanRepaymentHistoryRepository;
    private final AccountRepository accountRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AutoRepaymentExecutionResult executeAfterPayment(Long memberId, CardTransaction transaction) {
        LoanHistory loanHistory = loanHistoryRepository
                .findByCard_CardIdAndStatus(transaction.getCard().getCardId(), "ACTIVE")
                .orElse(null);

        if (loanHistory == null) {
            return AutoRepaymentExecutionResult.notApplied();
        }

        AutoRepaymentDecisionResponse decision = autoRepaymentPolicyService.decideAutoRepayment(
                memberId,
                transaction.getTransactionId()
        );

        BigDecimal ratio = nullSafe(decision.getFinalRepaymentRatio());
        if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
            return AutoRepaymentExecutionResult.fromDecision(decision, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    nullSafe(loanHistory.getRemainingPrincipal()), false);
        }

        Account account = accountRepository.findByIdForUpdate(transaction.getCard().getAccountId())
                .orElseThrow(() -> new IllegalStateException(
                        "자동상환용 계좌를 찾을 수 없습니다. accountId=" + transaction.getCard().getAccountId()
                ));

        BigDecimal repaymentAmount = calculateRepaymentAmount(transaction.getAmount(), ratio, loanHistory);
        if (repaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return AutoRepaymentExecutionResult.fromDecision(decision, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    nullSafe(loanHistory.getRemainingPrincipal()), false);
        }

        account.withdraw(repaymentAmount);
        BigDecimal appliedAmount = loanHistory.applyRepayment(repaymentAmount);

        loanRepaymentHistoryRepository.save(LoanRepaymentHistory.create(
                loanHistory,
                transaction,
                appliedAmount,
                toPercent(ratio),
                OffsetDateTime.now(),
                loanHistory.getRemainingPrincipal()
        ));

        return AutoRepaymentExecutionResult.fromDecision(
                decision,
                appliedAmount,
                nullSafe(loanHistory.getRemainingPrincipal()),
                appliedAmount.compareTo(BigDecimal.ZERO) > 0
        );
    }

    private BigDecimal calculateRepaymentAmount(
            BigDecimal transactionAmount,
            BigDecimal ratio,
            LoanHistory loanHistory
    ) {
        BigDecimal requestedAmount = nullSafe(transactionAmount)
                .multiply(ratio)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal remainingPrincipal = nullSafe(loanHistory.getRemainingPrincipal());
        return requestedAmount.min(remainingPrincipal).max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal toPercent(BigDecimal ratio) {
        return nullSafe(ratio)
                .multiply(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? ZERO : value;
    }

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
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    null
            );
        }

        public static AutoRepaymentExecutionResult failed() {
            return new AutoRepaymentExecutionResult(
                    false,
                    "HOLD",
                    "FAILED",
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
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
                    : decision.getFinalRepaymentRatio().multiply(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);

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
