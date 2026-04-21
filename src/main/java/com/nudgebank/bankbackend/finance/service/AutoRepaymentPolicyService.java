package com.nudgebank.bankbackend.finance.service;

import com.nudgebank.bankbackend.finance.dto.AutoRepaymentDecisionResponse;
import com.nudgebank.bankbackend.finance.dto.BaseRatioReason;
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
    private static final BigDecimal MAX_RATIO = new BigDecimal("0.10");

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
            return buildBlockedResponse(transactionId, finalBaseline, financialStatus, "BLOCKED", "가용 잔액이 없어 자동상환을 보류합니다.");
        }

        BaseRatioReason baseRatioReason = calculateBaseRepaymentRatio(finalBaseline);
        PolicyOutcome policyOutcome = applyFinancialAdjustments(baseRatioReason, finalBaseline, financialStatus);

        return AutoRepaymentDecisionResponse.builder()
                .memberId(memberId)
                .transactionId(transactionId)
                .baseRepaymentRatio(baseRatioReason.getBaseRatio())
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

    private BaseRatioReason calculateBaseRepaymentRatio(FinalBaselineResponse finalBaseline) {
        BigDecimal ratio = new BigDecimal("0.03");
        List<String> reasons = new ArrayList<>();
        reasons.add("기본 자동상환 비율은 3%입니다.");

        if (nullSafe(finalBaseline.getEssentialRatio()).compareTo(new BigDecimal("0.60")) >= 0) {
            ratio = ratio.subtract(new BigDecimal("0.01"));
            reasons.add("필수 소비 비율이 높아 1% 하향 조정합니다.");
        }

        if (nullSafe(finalBaseline.getDiscretionaryRatio()).compareTo(new BigDecimal("0.35")) >= 0) {
            ratio = ratio.add(new BigDecimal("0.01"));
            reasons.add("선택 소비 비율이 높아 1% 상향 조정합니다.");
        }

        if (nullSafe(finalBaseline.getRiskRatio()).compareTo(new BigDecimal("0.20")) >= 0) {
            ratio = ratio.add(new BigDecimal("0.02"));
            reasons.add("위험 소비 비율이 높아 2% 상향 조정합니다.");
        }

        BigDecimal avgSpending = resolvePolicyAvgSpending(finalBaseline);
        BigDecimal volatility = nullSafe(finalBaseline.getVolatility());
        if (avgSpending.compareTo(ZERO) > 0) {
            BigDecimal volatilityRatio = volatility.divide(avgSpending, 4, RoundingMode.HALF_UP);
            if (volatilityRatio.compareTo(new BigDecimal("1.00")) >= 0) {
                ratio = ratio.subtract(new BigDecimal("0.01"));
                reasons.add("변동성이 높아 1% 하향 조정합니다.");
            } else if (volatilityRatio.compareTo(new BigDecimal("0.40")) <= 0) {
                ratio = ratio.add(new BigDecimal("0.01"));
                reasons.add("변동성이 낮아 1% 상향 조정합니다.");
            }
        }

        return new BaseRatioReason(clamp(ratio, new BigDecimal("0.00"), new BigDecimal("0.10")), reasons);
    }

    private PolicyOutcome applyFinancialAdjustments(
            BaseRatioReason baseRatioReason,
            FinalBaselineResponse finalBaseline,
            FinancialStatusResponse financialStatus
    ) {
        BigDecimal adjustedRatio = baseRatioReason.getBaseRatio();
        List<String> reasons = baseRatioReason.getReasons();

        BigDecimal availableBalance = nullSafe(financialStatus.getAvailableBalance());
        BigDecimal avgSpending = resolvePolicyAvgSpending(finalBaseline);
        BigDecimal monthlyIncome = nullSafe(financialStatus.getMonthlyIncome());
        BigDecimal currentMonthSpendingAmount = nullSafe(financialStatus.getCurrentMonthSpendingAmount());
        BigDecimal totalLoanRemainingPrincipal = nullSafe(financialStatus.getTotalLoanRemainingPrincipal());
        Integer daysUntilPaymentDue = financialStatus.getDaysUntilPaymentDue();

        if (avgSpending.compareTo(ZERO) > 0) {
            BigDecimal balanceCoverage = availableBalance.divide(avgSpending, 4, RoundingMode.HALF_UP);
            if (balanceCoverage.compareTo(new BigDecimal("1.50")) >= 0) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.02"));
                reasons.add("가용 잔액이 평균 소비 대비 충분하여 2% 상향 조정합니다.");
            } else if (balanceCoverage.compareTo(new BigDecimal("0.30")) < 0) {
                adjustedRatio = adjustedRatio.subtract(new BigDecimal("0.02"));
                reasons.add("가용 잔액이 평균 소비 대비 부족하여 2% 하향 조정합니다.");
            }
        }

        if (monthlyIncome.compareTo(ZERO) > 0) {
            BigDecimal spendingPressure = currentMonthSpendingAmount.divide(monthlyIncome, 4, RoundingMode.HALF_UP);
            BigDecimal loanBurden = totalLoanRemainingPrincipal.divide(monthlyIncome, 4, RoundingMode.HALF_UP);

            if (spendingPressure.compareTo(new BigDecimal("0.80")) >= 0) {
                adjustedRatio = adjustedRatio.subtract(new BigDecimal("0.02"));
                reasons.add("이번 달 지출 비중이 높아 2% 하향 조정합니다.");
            } else if (spendingPressure.compareTo(new BigDecimal("0.50")) < 0) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.01"));
                reasons.add("이번 달 지출 비중이 낮아 2% 상향 조정합니다.");
            }

            if (loanBurden.compareTo(new BigDecimal("2.00")) >= 0) {
                adjustedRatio = adjustedRatio.subtract(new BigDecimal("0.01"));
                reasons.add("대출 잔액 부담이 커 1% 하향 조정합니다.");
            } else if (loanBurden.compareTo(BigDecimal.ONE) < 0) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.01"));
                reasons.add("대출 잔액 부담이 낮아 1% 상향 조정합니다.");
            }
        }

        if (daysUntilPaymentDue != null) {
            if (daysUntilPaymentDue <= 3) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.03"));
                reasons.add("상환 기일이 임박해 3% 상향 조정합니다.");
            } else if (daysUntilPaymentDue <= 7) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.02"));
                reasons.add("상환 기일이 가까워져 2% 상향 조정합니다.");
            } else if (daysUntilPaymentDue <= 14) {
                adjustedRatio = adjustedRatio.add(new BigDecimal("0.01"));
                reasons.add("상환 준비 기간이므로 1% 상향 조정합니다.");
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

    private BigDecimal resolvePolicyAvgSpending(FinalBaselineResponse finalBaseline) {
        BigDecimal personalAvgSpending = nullSafe(finalBaseline.getPersonalAvgSpending());
        BigDecimal ageAvgSpending = nullSafe(finalBaseline.getAgeAvgSpending());

        if (nullSafe(finalBaseline.getPersonalBaselineWeight()).compareTo(ZERO) > 0
                && personalAvgSpending.compareTo(ZERO) > 0) {
            return personalAvgSpending;
        }
        return ageAvgSpending;
    }

    private String resolveAction(BigDecimal ratio) {
        if (ratio.compareTo(new BigDecimal("0.00")) == 0) {
            return "HOLD";
        }
        if (ratio.compareTo(new BigDecimal("0.03")) <= 0) {
            return "MINIMUM";
        }
        if (ratio.compareTo(new BigDecimal("0.07")) <= 0) {
            return "STANDARD";
        }
        return "BOOST";
    }

    private String resolveGrade(BigDecimal ratio) {
        if (ratio.compareTo(new BigDecimal("0.00")) == 0) {
            return "BLOCKED";
        }
        if (ratio.compareTo(new BigDecimal("0.03")) <= 0) {
            return "DEFENSIVE";
        }
        if (ratio.compareTo(new BigDecimal("0.07")) <= 0) {
            return "BALANCED";
        }
        return "PROACTIVE";
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
