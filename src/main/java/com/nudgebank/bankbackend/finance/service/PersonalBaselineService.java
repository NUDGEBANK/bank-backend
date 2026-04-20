package com.nudgebank.bankbackend.finance.service;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.card.repository.CardTransactionRepository;
import com.nudgebank.bankbackend.finance.domain.AgeGroupBaseline;
import com.nudgebank.bankbackend.finance.domain.ConsumerBaseline;
import com.nudgebank.bankbackend.finance.domain.ConsumerMonthlyAnalysis;
import com.nudgebank.bankbackend.finance.domain.ConsumptionType;
import com.nudgebank.bankbackend.finance.dto.ConsumerBaselineResponse;
import com.nudgebank.bankbackend.finance.dto.FinalBaselineResponse;
import com.nudgebank.bankbackend.finance.dto.FinancialStatusResponse;
import com.nudgebank.bankbackend.finance.repository.AgeGroupBaselineRepository;
import com.nudgebank.bankbackend.finance.repository.ConsumerBaselineRepository;
import com.nudgebank.bankbackend.finance.repository.ConsumerMonthlyAnalysisRepository;
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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class PersonalBaselineService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String INTERNAL_BANK_CATEGORY_NAME = "넛지뱅크";
    private static final String LOAN_CATEGORY_NAME = "대출";
    private static final String LOAN_DISBURSEMENT_CATEGORY_NAME = "대출 실행 입금";
    private static final String LOAN_MARKET_NAME = "NudgeBank 대출 실행";
    private static final String AUTO_REPAYMENT_MENU_NAME = "대출금 자동상환";
    private static final String LOAN_KEYWORD = "대출";
    private static final String REPAYMENT_KEYWORD = "상환";

    private final MemberRepository memberRepository;
    private final AgeGroupBaselineRepository ageGroupBaselineRepository;
    private final ConsumerBaselineRepository consumerBaselineRepository;
    private final ConsumerMonthlyAnalysisRepository consumerMonthlyAnalysisRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final ConsumptionTypeClassifier consumptionTypeClassifier;
    private final FinancialStatusService financialStatusService;

    @Transactional(readOnly = true)
    public ConsumerBaselineResponse getLatestConsumerBaseline(Long memberId) {
        return consumerBaselineRepository.findTopByMemberIdOrderByAnalysisYearMonthDesc(memberId)
                .map(this::toConsumerBaselineResponse)
                .orElse(null);
    }

    public FinalBaselineResponse calculateAndGetFinalBaseline(Long memberId, Long transactionId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다. memberId=" + memberId));

        int age = calculateAge(member);
        String ageGroup = toAgeGroup(age);

        AgeGroupBaseline ageBaseline = ageGroupBaselineRepository.findById(ageGroup)
                .orElseThrow(() -> new EntityNotFoundException("연령 baseline이 없습니다. ageGroup=" + ageGroup));

        FinancialStatusResponse financialStatus = financialStatusService.getFinancialStatus(memberId, transactionId);

        OffsetDateTime firstTransactionDatetime = cardTransactionRepository.findFirstTransactionDatetimeByMemberId(memberId);
        if (firstTransactionDatetime == null) {
            return buildAgeOnlyResponse(memberId, age, ageBaseline,  financialStatus);
        }

        LocalDate today = LocalDate.now(KST);
        LocalDate firstTransactionDate = firstTransactionDatetime.atZoneSameInstant(KST).toLocalDate();
        LocalDate ninetyDaysAgo = today.minusDays(90);
        LocalDate baselineStartDate = firstTransactionDate.isAfter(ninetyDaysAgo) ? firstTransactionDate : ninetyDaysAgo;
        LocalDate baselineEndDate = today.plusDays(1); // end exclusive

        List<CardTransaction> transactions = cardTransactionRepository.findByMemberIdAndTransactionDatetimeGreaterThanEqualAndTransactionDatetimeLessThan(
                memberId,
                baselineStartDate.atStartOfDay(KST).toOffsetDateTime(),
                baselineEndDate.atStartOfDay(KST).toOffsetDateTime()
        );

        List<CardTransaction> consumptionTransactions = transactions.stream()
                .filter(transaction -> !isExcludedFromConsumptionMetrics(transaction))
                .toList();

        if (consumptionTransactions.isEmpty()) {
            return buildAgeOnlyResponse(memberId, age, ageBaseline, financialStatus);
        }

        PersonalMetrics personal = calculatePersonalMetrics(baselineStartDate, today, consumptionTransactions);
        ConsumerBaseline baseline = saveOrUpdateConsumerBaseline(memberId, personal, today);
        saveOrUpdateConsumerMonthlyAnalysis(memberId, today, baseline);

        Weight weight = resolveWeight(firstTransactionDate, today);



        return FinalBaselineResponse.builder()
                .memberId(memberId)
                .ageGroup(ageGroup)
                .age(age)
                .ageBaselineWeight(weight.ageWeight())
                .personalBaselineWeight(weight.personalWeight())
                .baselineStartDate(baselineStartDate)
                .baselineEndDate(today)
                .usageDays((int) ChronoUnit.DAYS.between(firstTransactionDate, today))
                .ageAvgSpending(ageBaseline.getAvgSpending())
                .personalAvgSpending(personal.avgSpending())
                .essentialRatio(weighted(ageBaseline.getEssentialRatio(), personal.essentialRatio(), weight))
                .normalRatio(weighted(ageBaseline.getNormalRatio(), personal.normalRatio(), weight))
                .discretionaryRatio(weighted(ageBaseline.getDiscretionaryRatio(), personal.discretionaryRatio(), weight))
                .riskRatio(weighted(ageBaseline.getRiskRatio(), personal.riskRatio(), weight))
                .volatility(weighted(ageBaseline.getVolatility(), personal.volatility(), weight))
                .baselineSource(weight.personalWeight().compareTo(BigDecimal.ZERO) == 0 ? "AGE_ONLY"
                        : weight.ageWeight().compareTo(BigDecimal.ZERO) == 0 ? "PERSONAL_ONLY"
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

    private ConsumerBaseline saveOrUpdateConsumerBaseline(Long memberId, PersonalMetrics personal, LocalDate today) {
        OffsetDateTime now = OffsetDateTime.now(KST);
        LocalDate analysisYearMonth = today.withDayOfMonth(1);
        BigDecimal volatilityIndex = calculateVolatilityIndex(personal.volatility());
        consumerBaselineRepository.upsert(
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
        );
        return consumerBaselineRepository.findByMemberIdAndAnalysisYearMonth(memberId, analysisYearMonth)
                .orElseThrow(() -> new IllegalStateException("개인 소비 baseline 저장 후 조회에 실패했습니다."));
    }

    private void saveOrUpdateConsumerMonthlyAnalysis(Long memberId, LocalDate today, ConsumerBaseline baseline) {
        OffsetDateTime now = OffsetDateTime.now(KST);
        LocalDate analysisYearMonth = today.withDayOfMonth(1);
        OffsetDateTime startOfMonth = analysisYearMonth.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime startOfNextMonth = analysisYearMonth.plusMonths(1).atStartOfDay(KST).toOffsetDateTime();

        List<CardTransaction> currentMonthTransactions = cardTransactionRepository.findByMemberIdAndTransactionDatetimeGreaterThanEqualAndTransactionDatetimeLessThan(
                memberId,
                startOfMonth,
                startOfNextMonth
        ).stream()
                .filter(transaction -> !isExcludedFromConsumptionMetrics(transaction))
                .toList();

        BigDecimal currentMonthSpending = currentMonthTransactions.stream()
                .map(CardTransaction::getAmount)
                .map(this::nullSafe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        int totalTransactionsCount = currentMonthTransactions.size();
        int essentialTransactionsCount = 0;
        int discretionaryTransactionsCount = 0;
        Map<Long, BigDecimal> spendingByCategoryId = new java.util.HashMap<>();

        for (CardTransaction transaction : currentMonthTransactions) {
            ConsumptionType type = consumptionTypeClassifier.classify(transaction);
            if (type == ConsumptionType.ESSENTIAL) {
                essentialTransactionsCount++;
            }
            if (type == ConsumptionType.DISCRETIONARY || type == ConsumptionType.RISK) {
                discretionaryTransactionsCount++;
            }

            if (transaction.getCategory() != null && transaction.getCategory().getCategoryId() != null) {
                spendingByCategoryId.merge(
                        transaction.getCategory().getCategoryId(),
                        nullSafe(transaction.getAmount()),
                        BigDecimal::add
                );
            }
        }

        Long largestSpendingCategoryId = null;
        BigDecimal largestSpendingAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Map.Entry<Long, BigDecimal> maxEntry = spendingByCategoryId.entrySet().stream()
                .max(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                .orElse(null);
        if (maxEntry != null) {
            largestSpendingCategoryId = maxEntry.getKey();
            largestSpendingAmount = nullSafe(maxEntry.getValue()).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal sameDayAvgSpending = baseline == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : nullSafe(baseline.getAvgSpending())
                .multiply(BigDecimal.valueOf(totalTransactionsCount))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal spendingDiffAmount = currentMonthSpending.subtract(sameDayAvgSpending).setScale(2, RoundingMode.HALF_UP);
        String spendingStatus = resolveSpendingStatus(currentMonthSpending, sameDayAvgSpending, spendingDiffAmount);

        consumerMonthlyAnalysisRepository.upsert(
                memberId,
                analysisYearMonth,
                currentMonthSpending,
                sameDayAvgSpending,
                spendingDiffAmount,
                spendingStatus,
                totalTransactionsCount,
                essentialTransactionsCount,
                discretionaryTransactionsCount,
                largestSpendingCategoryId,
                largestSpendingAmount,
                now
        );
    }

    private FinalBaselineResponse buildAgeOnlyResponse(Long memberId, int age, AgeGroupBaseline baseline, FinancialStatusResponse financialStatusResponse) {
        return FinalBaselineResponse.builder()
                .memberId(memberId)
                .ageGroup(baseline.getAgeGroup())
                .age(age)
                .ageBaselineWeight(BigDecimal.ONE)
                .personalBaselineWeight(BigDecimal.ZERO)
                .ageAvgSpending(baseline.getAvgSpending())
                .personalAvgSpending(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .essentialRatio(baseline.getEssentialRatio())
                .normalRatio(baseline.getNormalRatio())
                .discretionaryRatio(baseline.getDiscretionaryRatio())
                .riskRatio(baseline.getRiskRatio())
                .volatility(baseline.getVolatility())
                .baselineSource("AGE_ONLY")
                .financialStatusResponse(financialStatusResponse)
                .build();
    }

    private ConsumerBaselineResponse toConsumerBaselineResponse(ConsumerBaseline baseline) {
        return new ConsumerBaselineResponse(
                baseline.getBaselineId(),
                baseline.getAnalysisYearMonth(),
                baseline.getAvgSpending(),
                baseline.getEssentialRatio(),
                baseline.getNormalRatio(),
                baseline.getDiscretionaryRatio(),
                baseline.getRiskRatio(),
                baseline.getVolatility(),
                baseline.getVolatilityIndex(),
                baseline.getCreatedAt(),
                baseline.getUpdatedAt()
        );
    }

    private Weight resolveWeight(LocalDate firstTransactionDate, LocalDate today) {
        long usageMonths = Math.max(0, ChronoUnit.MONTHS.between(firstTransactionDate.withDayOfMonth(1), today.withDayOfMonth(1)));
        if (usageMonths < 1) {
            return new Weight(BigDecimal.ONE, BigDecimal.ZERO);
        }
        if (usageMonths < 2) {
            return new Weight(new BigDecimal("0.8"), new BigDecimal("0.2"));
        }
        if (usageMonths < 3) {
            return new Weight(new BigDecimal("0.6"), new BigDecimal("0.4"));
        }
        if (usageMonths < 4) {
            return new Weight(new BigDecimal("0.4"), new BigDecimal("0.6"));
        }
        if (usageMonths < 5) {
            return new Weight(new BigDecimal("0.2"), new BigDecimal("0.8"));
        }
        return new Weight(BigDecimal.ZERO, BigDecimal.ONE);
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

    private boolean isExcludedFromConsumptionMetrics(CardTransaction transaction) {
        String categoryName = transaction.getCategory() != null ? safe(transaction.getCategory().getCategoryName()) : "";
        String marketName = transaction.getMarket() != null ? safe(transaction.getMarket().getMarketName()) : "";
        String menuName = safe(transaction.getMenuName());

        return INTERNAL_BANK_CATEGORY_NAME.equals(categoryName)
                || LOAN_CATEGORY_NAME.equals(categoryName)
                || LOAN_DISBURSEMENT_CATEGORY_NAME.equals(categoryName)
                || LOAN_MARKET_NAME.equals(marketName)
                || AUTO_REPAYMENT_MENU_NAME.equals(menuName)
                || containsKeyword(categoryName, LOAN_KEYWORD)
                || containsKeyword(marketName, LOAN_KEYWORD)
                || containsKeyword(menuName, LOAN_KEYWORD)
                || containsKeyword(menuName, REPAYMENT_KEYWORD);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean containsKeyword(String value, String keyword) {
        return value != null && !value.isBlank() && value.contains(keyword);
    }

    private String resolveSpendingStatus(
            BigDecimal currentMonthSpending,
            BigDecimal sameDayAvgSpending,
            BigDecimal spendingDiffAmount
    ) {
        if (currentMonthSpending.compareTo(BigDecimal.ZERO) <= 0) {
            return "NO_SPENDING";
        }
        if (sameDayAvgSpending.compareTo(BigDecimal.ZERO) <= 0) {
            return "INSUFFICIENT_BASELINE";
        }

        BigDecimal diffRatio = spendingDiffAmount.abs()
                .divide(sameDayAvgSpending, 4, RoundingMode.HALF_UP);
        if (diffRatio.compareTo(new BigDecimal("0.05")) <= 0) {
            return "STABLE";
        }
        return spendingDiffAmount.compareTo(BigDecimal.ZERO) > 0 ? "HIGHER_THAN_BASELINE" : "LOWER_THAN_BASELINE";
    }

    private int calculateAge(Member member) {
        if (member.getBirth() == null) {
            throw new IllegalArgumentException("회원의 birth 정보가 없습니다. memberId=" + member.getMemberId());
        }
        return Period.between(member.getBirth(), LocalDate.now(KST)).getYears();
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
