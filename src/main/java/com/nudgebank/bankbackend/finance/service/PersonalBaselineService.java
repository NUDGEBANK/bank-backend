package com.nudgebank.bankbackend.finance.service;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.card.repository.CardTransactionRepository;
import com.nudgebank.bankbackend.finance.domain.AgeGroupBaseline;
import com.nudgebank.bankbackend.finance.domain.ConsumerBaseline;
import com.nudgebank.bankbackend.finance.domain.ConsumptionType;
import com.nudgebank.bankbackend.finance.dto.FinalBaselineResponse;
import com.nudgebank.bankbackend.finance.dto.FinancialStatusResponse;
import com.nudgebank.bankbackend.finance.repository.AgeGroupBaselineRepository;
import com.nudgebank.bankbackend.finance.repository.ConsumerBaselineRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class PersonalBaselineService {

    private final MemberRepository memberRepository;
    private final AgeGroupBaselineRepository ageGroupBaselineRepository;
    private final ConsumerBaselineRepository consumerBaselineRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final ConsumptionTypeClassifier consumptionTypeClassifier;
    private final FinancialStatusService financialStatusService;

    public FinalBaselineResponse calculateAndGetFinalBaseline(Long memberId, Long transactionId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다. memberId=" + memberId));

        int age = calculateAge(member);
        String ageGroup = toAgeGroup(age);

        AgeGroupBaseline ageBaseline = ageGroupBaselineRepository.findById(ageGroup)
                .orElseThrow(() -> new EntityNotFoundException("연령 baseline이 없습니다. ageGroup=" + ageGroup));

        OffsetDateTime firstTransactionDatetime = cardTransactionRepository.findFirstTransactionDatetimeByMemberId(memberId);
        if (firstTransactionDatetime == null) {
            return buildAgeOnlyResponse(memberId, age, ageBaseline);
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate firstTransactionDate = firstTransactionDatetime.toLocalDate();
        LocalDate ninetyDaysAgo = today.minusDays(90);
        LocalDate baselineStartDate = firstTransactionDate.isAfter(ninetyDaysAgo) ? firstTransactionDate : ninetyDaysAgo;
        LocalDate baselineEndDate = today.plusDays(1); // end exclusive

        List<CardTransaction> transactions = cardTransactionRepository.findByMemberIdAndTransactionDatetimeBetween(
                memberId,
                baselineStartDate.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime(),
                baselineEndDate.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime()
        );

        if (transactions.isEmpty()) {
            return buildAgeOnlyResponse(memberId, age, ageBaseline);
        }

        PersonalMetrics personal = calculatePersonalMetrics(baselineStartDate, today, transactions);
        saveOrUpdateConsumerBaseline(memberId, personal, today);

        Weight weight = resolveWeight(firstTransactionDate, today);

        FinancialStatusResponse financialStatus = financialStatusService.getFinancialStatus(memberId, transactionId);

        return FinalBaselineResponse.builder()
                .memberId(memberId)
                .ageGroup(ageGroup)
                .age(age)
                .ageBaselineWeight(weight.ageWeight())
                .personalBaselineWeight(weight.personalWeight())
                .baselineStartDate(baselineStartDate)
                .baselineEndDate(today)
                .usageDays((int) ChronoUnit.DAYS.between(firstTransactionDate, today))
                .avgSpending(weighted(ageBaseline.getAvgSpending(), personal.avgSpending(), weight))
                .essentialRatio(weighted(ageBaseline.getEssentialRatio(), personal.essentialRatio(), weight))
                .normalRatio(weighted(ageBaseline.getNormalRatio(), personal.normalRatio(), weight))
                .discretionaryRatio(weighted(ageBaseline.getDiscretionaryRatio(), personal.discretionaryRatio(), weight))
                .riskRatio(weighted(ageBaseline.getRiskRatio(), personal.riskRatio(), weight))
                .volatility(weighted(ageBaseline.getVolatility(), personal.volatility(), weight))
                .baselineSource(weight.personalWeight().compareTo(BigDecimal.ZERO) == 0 ? "AGE_ONLY"
                        : weight.personalWeight().compareTo(new BigDecimal("0.7")) >= 0 ? "PERSONAL_HEAVY"
                        : "MIXED")
                .financialStatusResponse(financialStatus)
                .build();
    }

    private PersonalMetrics calculatePersonalMetrics(
            LocalDate startDate,
            LocalDate endDate,
            List<CardTransaction> transactions
    ) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        Map<ConsumptionType, BigDecimal> amountByType = new EnumMap<>(ConsumptionType.class);
        for (ConsumptionType type : ConsumptionType.values()) {
            amountByType.put(type, BigDecimal.ZERO);
        }

        Map<LocalDate, BigDecimal> dailyTotals = new java.util.LinkedHashMap<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            dailyTotals.put(cursor, BigDecimal.ZERO);
            cursor = cursor.plusDays(1);
        }

        for (CardTransaction transaction : transactions) {
            BigDecimal amount = nullSafe(transaction.getAmount());
            totalAmount = totalAmount.add(amount);

            ConsumptionType type = consumptionTypeClassifier.classify(transaction);
            amountByType.put(type, amountByType.get(type).add(amount));

            LocalDate txDate = transaction.getTransactionDatetime().toLocalDate();
            dailyTotals.put(txDate, dailyTotals.getOrDefault(txDate, BigDecimal.ZERO).add(amount));
        }

        int count = transactions.size();
        BigDecimal avgSpending = count == 0
                ? BigDecimal.ZERO
                : totalAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        return new PersonalMetrics(
                avgSpending,
                ratio(amountByType.get(ConsumptionType.ESSENTIAL), totalAmount),
                ratio(amountByType.get(ConsumptionType.NORMAL), totalAmount),
                ratio(amountByType.get(ConsumptionType.DISCRETIONARY), totalAmount),
                ratio(amountByType.get(ConsumptionType.RISK), totalAmount),
                calculateStdDev(new ArrayList<>(dailyTotals.values()))
        );
    }

    private void saveOrUpdateConsumerBaseline(Long memberId, PersonalMetrics personal, LocalDate today) {
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate analysisYearMonth = today.withDayOfMonth(1);
        BigDecimal volatilityIndex = calculateVolatilityIndex(personal.volatility());

        consumerBaselineRepository.findByMemberIdAndAnalysisYearMonth(memberId, analysisYearMonth)
                .ifPresentOrElse(
                        baseline -> baseline.update(
                                personal.avgSpending(),
                                personal.essentialRatio(),
                                personal.normalRatio(),
                                personal.discretionaryRatio(),
                                personal.riskRatio(),
                                personal.volatility(),
                                volatilityIndex,
                                analysisYearMonth,
                                now
                        ),
                        () -> consumerBaselineRepository.save(
                                ConsumerBaseline.create(
                                        memberId,
                                        personal.avgSpending(),
                                        personal.essentialRatio(),
                                        personal.normalRatio(),
                                        personal.discretionaryRatio(),
                                        personal.riskRatio(),
                                        personal.volatility(),
                                        volatilityIndex,
                                        analysisYearMonth,
                                        now
                                )
                        )
                );
    }

    private FinalBaselineResponse buildAgeOnlyResponse(Long memberId, int age, AgeGroupBaseline baseline) {
        return FinalBaselineResponse.builder()
                .memberId(memberId)
                .ageGroup(baseline.getAgeGroup())
                .age(age)
                .ageBaselineWeight(BigDecimal.ONE)
                .personalBaselineWeight(BigDecimal.ZERO)
                .avgSpending(baseline.getAvgSpending())
                .essentialRatio(baseline.getEssentialRatio())
                .normalRatio(baseline.getNormalRatio())
                .discretionaryRatio(baseline.getDiscretionaryRatio())
                .riskRatio(baseline.getRiskRatio())
                .volatility(baseline.getVolatility())
                .baselineSource("AGE_ONLY")
                .build();
    }

    private Weight resolveWeight(LocalDate firstTransactionDate, LocalDate today) {
        long usageDays = Math.max(0, ChronoUnit.DAYS.between(firstTransactionDate, today));
        if (usageDays < 30) {
            return new Weight(new BigDecimal("0.9"), new BigDecimal("0.1"));
        }
        if (usageDays < 90) {
            return new Weight(new BigDecimal("0.6"), new BigDecimal("0.4"));
        }
        if (usageDays < 180) {
            return new Weight(new BigDecimal("0.3"), new BigDecimal("0.7"));
        }
        return new Weight(new BigDecimal("0.1"), new BigDecimal("0.9"));
    }

    private BigDecimal weighted(BigDecimal ageValue, BigDecimal personalValue, Weight weight) {
        return nullSafe(ageValue).multiply(weight.ageWeight())
                .add(nullSafe(personalValue).multiply(weight.personalWeight()))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return nullSafe(part).divide(total, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateStdDev(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double[] arr = values.stream().mapToDouble(BigDecimal::doubleValue).toArray();
        double mean = java.util.Arrays.stream(arr).average().orElse(0.0);
        double variance = java.util.Arrays.stream(arr)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        return BigDecimal.valueOf(Math.sqrt(variance)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateVolatilityIndex(BigDecimal volatility) {
        if (volatility == null || volatility.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        // TODO: 연령대 평균 대비 지수 계산 기준이 확정되면 교체
        return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
    }

    private int calculateAge(Member member) {
        if (member.getBirth() == null) {
            throw new IllegalArgumentException("회원의 birth 정보가 없습니다. memberId=" + member.getMemberId());
        }
        return Period.between(member.getBirth(), LocalDate.now()).getYears();
    }

    private String toAgeGroup(int age) {
        if (age < 20) return "10s";
        if (age < 30) return "20s";
        if (age < 40) return "30s";
        if (age < 50) return "40s";
        if (age < 60) return "50s";
        return "60s+";
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record PersonalMetrics(
            BigDecimal avgSpending,
            BigDecimal essentialRatio,
            BigDecimal normalRatio,
            BigDecimal discretionaryRatio,
            BigDecimal riskRatio,
            BigDecimal volatility
    ) {
    }

    private record Weight(BigDecimal ageWeight, BigDecimal personalWeight) {
    }
}
