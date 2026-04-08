package com.nudgebank.bankbackend.finance.service;

import com.nudgebank.bankbackend.finance.dto.AutoRepaymentDecisionResponse;
import com.nudgebank.bankbackend.finance.dto.FinalBaselineResponse;
import com.nudgebank.bankbackend.finance.dto.FinancialStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AutoRepaymentPolicyService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal MIN_RATIO = new BigDecimal("0.00");
    private static final BigDecimal MAX_RATIO = new BigDecimal("0.60");

    private final PersonalBaselineService personalBaselineService;

    public AutoRepaymentDecisionResponse decideAutoRepayment(Long memberId, Long transactionId) {
        FinalBaselineResponse finalBaseline = personalBaselineService.calculateAndGetFinalBaseline(memberId, transactionId);
        FinancialStatusResponse financialStatus = finalBaseline.getFinancialStatusResponse();

        BigDecimal totalLoanRemainingPrincipal = nullSafe(financialStatus.getTotalLoanRemainingPrincipal());
        BigDecimal availableBalance = nullSafe(financialStatus.getAvailableBalance());

        if (totalLoanRemainingPrincipal.compareTo(ZERO) <= 0) {
            return buildNoLoanResponse(transactionId, finalBaseline, financialStatus);
        }

        if (availableBalance.compareTo(ZERO) <= 0) {
            return buildBlockedResponse(transactionId, finalBaseline, financialStatus, "HOLD", "가용 잔액이 없어 자동상환을 보류합니다.");
        }

        BigDecimal baseRatio = calculateBaseRepaymentRatio(finalBaseline);
        PolicyOutcome policyOutcome = applyFinancialAdjustments(baseRatio, finalBaseline, financialStatus);

        return AutoRepaymentDecisionResponse.builder()
                .memberId(memberId)
                .transactionId(transactionId)
                .baseRepaymentRatio(baseRatio)
                .finalRepaymentRatio(policyOutcome.finalRatio())
                .repaymentAction(policyOutcome.action())
                .policyGrade(policyOutcome.grade())
                .availableBalance(availableBalance)
                .monthlyIncome(nullSafe(financialStatus.getMonthlyIncome()))
                .currentMonthSpendingAmount(nullSafe(financialStatus.getCurrentMonthSpendingAmount()))
                .totalLoanRemainingPrincipal(totalLoanRemainingPrincipal)
                .daysUntilPaymentDue(financialStatus.getDaysUntilPaymentDue())
                .essentialRatio(nullSafe(finalBaseline.getEssentialRatio()))
                .discretionaryRatio(nullSafe(finalBaseline.getDiscretionaryRatio()))
                .riskRatio(nullSafe(finalBaseline.getRiskRatio()))
                .volatility(nullSafe(finalBaseline.getVolatility()))
                .baselineSource(finalBaseline.getBaselineSource())
                .policyReason(policyOutcome.reason())
                .build();
    }

    private AutoRepaymentDecisionResponse buildNoLoanResponse(
            Long transactionId,
            FinalBaselineResponse finalBaseline,
            FinancialStatusResponse financialStatus
    ) {
        return AutoRepaymentDecisionResponse.builder()
                .memberId(finalBaseline.getMemberId())
                .transactionId(transactionId)
                .baseRepaymentRatio(ZERO.setScale(4, RoundingMode.HALF_UP))
                .finalRepaymentRatio(ZERO.setScale(4, RoundingMode.HALF_UP))
                .repaymentAction("HOLD")
                .policyGrade("NO_LOAN")
                .availableBalance(nullSafe(financialStatus.getAvailableBalance()))
                .monthlyIncome(nullSafe(financialStatus.getMonthlyIncome()))
                .currentMonthSpendingAmount(nullSafe(financialStatus.getCurrentMonthSpendingAmount()))
                .totalLoanRemainingPrincipal(nullSafe(financialStatus.getTotalLoanRemainingPrincipal()))
                .daysUntilPaymentDue(financialStatus.getDaysUntilPaymentDue())
                .essentialRatio(nullSafe(finalBaseline.getEssentialRatio()))
                .discretionaryRatio(nullSafe(finalBaseline.getDiscretionaryRatio()))
                .riskRatio(nullSafe(finalBaseline.getRiskRatio()))
                .volatility(nullSafe(finalBaseline.getVolatility()))
                .baselineSource(finalBaseline.getBaselineSource())
                .policyReason("활성 대출이 없어 자동상환 비율을 0으로 설정합니다.")
                .build();
    }

    private AutoRepaymentDecisionResponse buildBlockedResponse(
            Long transactionId,
            FinalBaselineResponse finalBaseline,
            FinancialStatusResponse financialStatus,
            String grade,
            String reason
    ) {
        return AutoRepaymentDecisionResponse.builder()
                .memberId(finalBaseline.getMemberId())
                .transactionId(transactionId)
                .baseRepaymentRatio(ZERO.setScale(4, RoundingMode.HALF_UP))
                .finalRepaymentRatio(ZERO.setScale(4, RoundingMode.HALF_UP))
                .repaymentAction("HOLD")
                .policyGrade(grade)
                .availableBalance(nullSafe(financialStatus.getAvailableBalance()))
                .monthlyIncome(nullSafe(financialStatus.getMonthlyIncome()))
                .currentMonthSpendingAmount(nullSafe(financialStatus.getCurrentMonthSpendingAmount()))
                .totalLoanRemainingPrincipal(nullSafe(financialStatus.getTotalLoanRemainingPrincipal()))
                .daysUntilPaymentDue(financialStatus.getDaysUntilPaymentDue())
                .essentialRatio(nullSafe(finalBaseline.getEssentialRatio()))
                .discretionaryRatio(nullSafe(finalBaseline.getDiscretionaryRatio()))
                .riskRatio(nullSafe(finalBaseline.getRiskRatio()))
                .volatility(nullSafe(finalBaseline.getVolatility()))
                .baselineSource(finalBaseline.getBaselineSource())
                .policyReason(reason)
                .build();
    }

    private BigDecimal calculateBaseRepaymentRatio(FinalBaselineResponse finalBaseline) {
        BigDecimal ratio = new BigDecimal("0.20");

        if (nullSafe(finalBaseline.getEssentialRatio()).compareTo(new BigDecimal("0.60")) >= 0) {
            ratio = ratio.subtract(new BigDecimal("0.05"));
        }

        if (nullSafe(finalBaseline.getDiscretionaryRatio()).compareTo(new BigDecimal("0.35")) >= 0) {
            ratio = ratio.add(new BigDecimal("0.05"));
        }

        if (nullSafe(finalBaseline.getRiskRatio()).compareTo(new BigDecimal("0.20")) >= 0) {
            ratio = ratio.subtract(new BigDecimal("0.05"));
        }

        BigDecimal avgSpending = nullSafe(finalBaseline.getAvgSpending());
        BigDecimal volatility = nullSafe(finalBaseline.getVolatility());
        if (avgSpending.compareTo(ZERO) > 0) {
            BigDecimal volatilityRatio = volatility.divide(avgSpending, 4, RoundingMode.HALF_UP);
            if (volatilityRatio.compareTo(new BigDecimal("1.00")) >= 0) {
                ratio = ratio.subtract(new BigDecimal("0.05"));
            } else if (volatilityRatio.compareTo(new BigDecimal("0.40")) <= 0) {
                ratio = ratio.add(new BigDecimal("0.05"));
            }
        }

        return clamp(ratio, new BigDecimal("0.05"), new BigDecimal("0.35"));
    }

    private PolicyOutcome applyFinancialAdjustments(
            BigDecimal baseRatio,
            FinalBaselineResponse finalBaseline,
            FinancialStatusResponse financialStatus
    ) {
        BigDecimal adjustedRatio = baseRatio;
        List<String> reasons = new ArrayList<>();

        BigDecimal availableBalance = nullSafe(financialStatus.getAvailableBalance());
        BigDecimal avgSpending = nullSafe(finalBaseline.getAvgSpending());
        BigDecimal monthlyIncome = nullSafe(financialStatus.getMonthlyIncome());
        BigDecimal currentMonthSpendingAmount = nullSafe(financialStatus.getCurrentMonthSpendingAmount());
        BigDecimal totalLoanRemainingPrincipal = nullSafe(financialStatus.getTotalLoanRemainingPrincipal());
        Integer daysUntilPaymentDue = financialStatus.getDaysUntilPaymentDue();

        if (avgSpending.compareTo(ZERO) > 0) {
            BigDecimal balanceCoverage = availableBalance.divide(avgSpending, 4, RoundingMode.HALF_UP);
            if (balanceCoverage.compareTo(new BigDecimal("1.50")) >= 0) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.10"));
                reasons.add("가용 잔액이 평균 소비 대비 충분합니다");
            } else if (balanceCoverage.compareTo(new BigDecimal("0.30")) < 0) {
                adjustedRatio = adjustedRatio.subtract(new BigDecimal("0.10"));
                reasons.add("가용 잔액이 평균 소비 대비 부족합니다");
            }
        }

        if (monthlyIncome.compareTo(ZERO) > 0) {
            BigDecimal spendingPressure = currentMonthSpendingAmount.divide(monthlyIncome, 4, RoundingMode.HALF_UP);
            BigDecimal loanBurden = totalLoanRemainingPrincipal.divide(monthlyIncome, 4, RoundingMode.HALF_UP);

            if (spendingPressure.compareTo(new BigDecimal("0.80")) >= 0) {
                adjustedRatio = adjustedRatio.subtract(new BigDecimal("0.10"));
                reasons.add("이번 달 지출 비중이 높습니다");
            } else if (spendingPressure.compareTo(new BigDecimal("0.50")) < 0) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.05"));
                reasons.add("이번 달 지출 비중이 낮습니다");
            }

            if (loanBurden.compareTo(new BigDecimal("2.00")) >= 0) {
                adjustedRatio = adjustedRatio.subtract(new BigDecimal("0.05"));
                reasons.add("대출 잔액 부담이 큽니다");
            } else if (loanBurden.compareTo(BigDecimal.ONE) < 0) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.05"));
                reasons.add("대출 잔액 부담이 상대적으로 낮습니다");
            }
        }

        if (daysUntilPaymentDue != null) {
            if (daysUntilPaymentDue <= 3) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.15"));
                reasons.add("상환 기일이 임박했습니다");
            } else if (daysUntilPaymentDue <= 7) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.10"));
                reasons.add("상환 기일이 가까워졌습니다");
            } else if (daysUntilPaymentDue <= 14) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.05"));
                reasons.add("상환 준비 구간입니다");
            }
        }

        adjustedRatio = clamp(adjustedRatio, MIN_RATIO, MAX_RATIO);

        String action = resolveAction(adjustedRatio);
        String grade = resolveGrade(adjustedRatio);
        String reason = reasons.isEmpty()
                ? "기본 소비 패턴과 재무 상태를 반영해 자동상환 비율을 유지합니다."
                : String.join(", ", reasons);

        return new PolicyOutcome(adjustedRatio, action, grade, reason);
    }

    private String resolveAction(BigDecimal ratio) {
        if (ratio.compareTo(new BigDecimal("0.00")) == 0) {
            return "HOLD";
        }
        if (ratio.compareTo(new BigDecimal("0.10")) <= 0) {
            return "MINIMUM";
        }
        if (ratio.compareTo(new BigDecimal("0.25")) <= 0) {
            return "STANDARD";
        }
        if (ratio.compareTo(new BigDecimal("0.40")) <= 0) {
            return "BOOST";
        }
        return "AGGRESSIVE";
    }

    private String resolveGrade(BigDecimal ratio) {
        if (ratio.compareTo(new BigDecimal("0.00")) == 0) {
            return "BLOCKED";
        }
        if (ratio.compareTo(new BigDecimal("0.10")) <= 0) {
            return "DEFENSIVE";
        }
        if (ratio.compareTo(new BigDecimal("0.25")) <= 0) {
            return "BALANCED";
        }
        if (ratio.compareTo(new BigDecimal("0.40")) <= 0) {
            return "PROACTIVE";
        }
        return "ACCELERATED";
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        BigDecimal clamped = value.max(min).min(max);
        return clamped.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private record PolicyOutcome(
            BigDecimal finalRatio,
            String action,
            String grade,
            String reason
    ) {
    }
}
