package com.nudgebank.bankbackend.credit.service;

import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.card.domain.Card;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.card.repository.CardRepository;
import com.nudgebank.bankbackend.card.repository.CardTransactionRepository;
import com.nudgebank.bankbackend.credit.domain.CreditHistory;
import com.nudgebank.bankbackend.credit.dto.CreditHistoryListResponse;
import com.nudgebank.bankbackend.credit.dto.CreditScoreResponse;
import com.nudgebank.bankbackend.credit.repository.CreditHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditScoreService {
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
  private static final Duration EVALUATION_COOLDOWN = Duration.ofMinutes(5);

  private final MemberRepository memberRepository;
  private final AccountRepository accountRepository;
  private final CardRepository cardRepository;
  private final CardTransactionRepository cardTransactionRepository;
  private final CreditHistoryRepository creditHistoryRepository;

  public CreditScoreService(
      MemberRepository memberRepository,
      AccountRepository accountRepository,
      CardRepository cardRepository,
      CardTransactionRepository cardTransactionRepository,
      CreditHistoryRepository creditHistoryRepository
  ) {
    this.memberRepository = memberRepository;
    this.accountRepository = accountRepository;
    this.cardRepository = cardRepository;
    this.cardTransactionRepository = cardTransactionRepository;
    this.creditHistoryRepository = creditHistoryRepository;
  }

  public CreditScoreResponse getLatest(Long memberId) {
    validateMember(memberId);

    CreditHistory latest = creditHistoryRepository
        .findTopByMemberIdOrderByEvaluatedAtDescCreditHistoryIdDesc(memberId)
        .orElseThrow(() -> new IllegalArgumentException("CREDIT_HISTORY_NOT_FOUND"));

    Integer previousScore = findPreviousScore(memberId, latest.getCreditHistoryId());
    return toResponse(latest, previousScore);
  }

  @Transactional
  public CreditScoreResponse evaluate(Long memberId) {
    validateMember(memberId);
    Optional<CreditHistory> latestOptional = creditHistoryRepository
        .findTopByMemberIdOrderByEvaluatedAtDescCreditHistoryIdDesc(memberId);

    if (latestOptional.isPresent() && isWithinCooldown(latestOptional.get())) {
      CreditHistory latest = latestOptional.get();
      Integer previousScore = findPreviousScore(memberId, latest.getCreditHistoryId());
      return toResponse(latest, previousScore);
    }

    Integer previousScore = latestOptional
        .map(CreditHistory::getCreditScore)
        .orElse(null);

    CreditHistory saved = evaluateAndSave(memberId);
    return toResponse(saved, previousScore);
  }

  public CreditHistoryListResponse getHistory(Long memberId) {
    validateMember(memberId);

    List<CreditHistoryListResponse.CreditHistoryItemDto> histories = creditHistoryRepository
        .findTop6ByMemberIdOrderByEvaluatedAtDescCreditHistoryIdDesc(memberId)
        .stream()
        .map(history -> new CreditHistoryListResponse.CreditHistoryItemDto(
            history.getCreditHistoryId(),
            history.getCreditScore(),
            history.getCreditGrade(),
            history.getEvaluationResult(),
            history.getEvaluatedAt() == null ? null : history.getEvaluatedAt().format(DATE_TIME_FORMATTER)
        ))
        .toList();

    return new CreditHistoryListResponse(true, "OK", histories);
  }

  private void validateMember(Long memberId) {
    if (memberId == null) {
      throw new IllegalArgumentException("UNAUTHORIZED");
    }
    if (!memberRepository.existsById(memberId)) {
      throw new IllegalArgumentException("MEMBER_NOT_FOUND");
    }
  }

  private boolean isWithinCooldown(CreditHistory latest) {
    if (latest.getEvaluatedAt() == null) {
      return false;
    }
    Duration elapsed = Duration.between(latest.getEvaluatedAt(), LocalDateTime.now());
    return !elapsed.isNegative() && elapsed.compareTo(EVALUATION_COOLDOWN) < 0;
  }

  private CreditHistory evaluateAndSave(Long memberId) {
    List<Account> accounts = accountRepository.findAllByMemberId(memberId);
    List<Card> cards = accounts.stream()
        .map(account -> cardRepository.findByAccountId(account.getAccountId()))
        .flatMap(Optional::stream)
        .toList();

    List<CardTransaction> transactions = cards.stream()
        .map(card -> cardTransactionRepository.findByCardCardIdOrderByTransactionDatetimeDesc(card.getCardId()))
        .flatMap(List::stream)
        .sorted(Comparator.comparing(CardTransaction::getTransactionDatetime).reversed())
        .toList();

    int score = calculateScore(accounts, cards, transactions);
    String grade = getGrade(score);
    String summary = buildSummary(accounts, transactions);

    CreditHistory creditHistory = CreditHistory.create(
        memberId,
        score,
        grade,
        summary,
        LocalDateTime.now()
    );

    return creditHistoryRepository.save(creditHistory);
  }

  private int calculateScore(List<Account> accounts, List<Card> cards, List<CardTransaction> transactions) {
    double score = 500;

    BigDecimal totalBalance = accounts.stream()
        .map(Account::getBalance)
        .filter(balance -> balance != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    double totalBalanceValue = totalBalance.doubleValue();
    double assetScore = calculateAssetScore(totalBalanceValue);

    Map<YearMonth, BigDecimal> monthlyTotals = buildMonthlyTotals(transactions, 3);
    BigDecimal averageMonthly = average(monthlyTotals.values().stream().toList());
    Map<YearMonth, Integer> monthlyTransactionCounts = buildMonthlyTransactionCounts(transactions, 3);
    int activeMonths = (int) monthlyTransactionCounts.values().stream()
        .filter(count -> count >= 2)
        .count();
    int recentTransactionCount = monthlyTransactionCounts.values().stream()
        .mapToInt(Integer::intValue)
        .sum();
    double averageMonthlyValue = averageMonthly.doubleValue();

    score += adjustAssetScoreByActivity(assetScore, activeMonths, recentTransactionCount);
    score += scaleScore(activeMonths, 0, 3, 95);
    score += calculateSpendingBurdenScore(totalBalanceValue, averageMonthlyValue);

    BigDecimal volatility = calculateVolatility(monthlyTotals, averageMonthly);
    score += calculateVolatilityScore(monthlyTotals.isEmpty(), volatility.doubleValue());

    score += scaleScore(recentTransactionCount, 0, 30, 60);
    score += calculateRecencyScore(transactions);

    if (activeMonths == 0 || recentTransactionCount < 3) {
      score -= 30;
    }

    int roundedScore = (int) Math.round(score);
    return Math.max(300, Math.min(950, roundedScore));
  }

  private Map<YearMonth, BigDecimal> buildMonthlyTotals(List<CardTransaction> transactions, int months) {
    OffsetDateTime cutoff = OffsetDateTime.now().minusMonths(months);
    Map<YearMonth, BigDecimal> monthlyTotals = new LinkedHashMap<>();

    for (CardTransaction transaction : transactions) {
      if (transaction.getTransactionDatetime() == null || transaction.getTransactionDatetime().isBefore(cutoff)) {
        continue;
      }

      YearMonth yearMonth = YearMonth.from(transaction.getTransactionDatetime());
      monthlyTotals.merge(yearMonth, transaction.getAmount(), BigDecimal::add);
    }

    return monthlyTotals;
  }

  private BigDecimal average(List<BigDecimal> values) {
    if (values.isEmpty()) {
      return BigDecimal.ZERO;
    }

    BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
  }

  private BigDecimal calculateVolatility(Map<YearMonth, BigDecimal> monthlyTotals, BigDecimal averageMonthly) {
    if (monthlyTotals.size() < 2 || averageMonthly.compareTo(BigDecimal.ZERO) == 0) {
      return BigDecimal.ZERO;
    }

    BigDecimal max = monthlyTotals.values().stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    BigDecimal min = monthlyTotals.values().stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    return max.subtract(min).divide(averageMonthly, 2, RoundingMode.HALF_UP);
  }

  private Map<YearMonth, Integer> buildMonthlyTransactionCounts(List<CardTransaction> transactions, int months) {
    OffsetDateTime cutoff = OffsetDateTime.now().minusMonths(months);
    Map<YearMonth, Integer> monthlyCounts = new LinkedHashMap<>();

    for (CardTransaction transaction : transactions) {
      if (transaction.getTransactionDatetime() == null || transaction.getTransactionDatetime().isBefore(cutoff)) {
        continue;
      }

      YearMonth yearMonth = YearMonth.from(transaction.getTransactionDatetime());
      monthlyCounts.merge(yearMonth, 1, Integer::sum);
    }

    return monthlyCounts;
  }

  private double calculateAssetScore(double totalBalanceValue) {
    if (totalBalanceValue <= 0) {
      return 0;
    }
    if (totalBalanceValue <= 10_000_000) {
      return scaleScore(totalBalanceValue, 0, 10_000_000, 55);
    }
    if (totalBalanceValue <= 30_000_000) {
      return 55 + scaleScore(totalBalanceValue - 10_000_000, 0, 20_000_000, 35);
    }
    if (totalBalanceValue <= 50_000_000) {
      return 90 + scaleScore(totalBalanceValue - 30_000_000, 0, 20_000_000, 20);
    }
    return 110;
  }

  private double adjustAssetScoreByActivity(double assetScore, int activeMonths, int recentTransactionCount) {
    if (assetScore <= 0) {
      return 0;
    }
    if (activeMonths >= 3 && recentTransactionCount >= 10) {
      return assetScore;
    }
    if (activeMonths >= 2 && recentTransactionCount >= 5) {
      return assetScore * 0.8;
    }
    if (activeMonths >= 1 && recentTransactionCount >= 3) {
      return assetScore * 0.6;
    }
    return assetScore * 0.35;
  }

  private double calculateSpendingBurdenScore(double totalBalanceValue, double averageMonthlyValue) {
    if (averageMonthlyValue <= 0) {
      return 0;
    }
    double referenceAsset = Math.max(totalBalanceValue, 300_000);
    double burdenRatio = averageMonthlyValue / referenceAsset;

    if (burdenRatio <= 0.15) {
      return 55;
    }
    if (burdenRatio <= 0.30) {
      return 55 - scaleScore(burdenRatio - 0.15, 0, 0.15, 15);
    }
    if (burdenRatio <= 0.60) {
      return 40 - scaleScore(burdenRatio - 0.30, 0, 0.30, 25);
    }
    if (burdenRatio <= 1.00) {
      return 15 - scaleScore(burdenRatio - 0.60, 0, 0.40, 35);
    }
    return -20 - scaleScore(Math.min(burdenRatio - 1.00, 1.50), 0, 1.50, 30);
  }

  private double calculateVolatilityScore(boolean noTransactions, double volatility) {
    if (noTransactions) {
      return 0;
    }
    if (volatility <= 0.20) {
      return 110;
    }
    if (volatility <= 0.40) {
      return 110 - scaleScore(volatility - 0.20, 0, 0.20, 40);
    }
    if (volatility <= 0.80) {
      return 70 - scaleScore(volatility - 0.40, 0, 0.40, 50);
    }
    return -scaleScore(Math.min(volatility - 0.80, 1.20), 0, 1.20, 45);
  }

  private double calculateRecencyScore(List<CardTransaction> transactions) {
    if (transactions.isEmpty()) {
      return -40;
    }

    OffsetDateTime latestTransactionAt = transactions.stream()
        .map(CardTransaction::getTransactionDatetime)
        .filter(transactionDatetime -> transactionDatetime != null)
        .max(Comparator.naturalOrder())
        .orElse(null);

    if (latestTransactionAt == null) {
      return -40;
    }

    long daysSinceLatest = java.time.Duration.between(latestTransactionAt, OffsetDateTime.now()).toDays();

    if (daysSinceLatest <= 14) {
      return 45;
    }
    if (daysSinceLatest <= 30) {
      return 30;
    }
    if (daysSinceLatest <= 60) {
      return 10;
    }
    if (daysSinceLatest <= 90) {
      return -10;
    }
    return -40;
  }

  private double scaleScore(double value, double minValue, double maxValue, double maxScore) {
    if (value <= minValue) {
      return 0;
    }
    if (value >= maxValue) {
      return maxScore;
    }
    return ((value - minValue) / (maxValue - minValue)) * maxScore;
  }

  private String buildSummary(List<Account> accounts, List<CardTransaction> transactions) {
    BigDecimal totalBalance = accounts.stream()
        .map(Account::getBalance)
        .filter(balance -> balance != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    Map<YearMonth, BigDecimal> monthlyTotals = buildMonthlyTotals(transactions, 3);
    BigDecimal averageMonthly = average(monthlyTotals.values().stream().toList());
    BigDecimal volatility = calculateVolatility(monthlyTotals, averageMonthly);

    List<String> sentences = new ArrayList<>();
    sentences.add(accounts.isEmpty()
        ? "연결된 계좌 정보가 적어 보수적으로 평가했습니다."
        : "연결된 계좌와 카드 거래 흐름을 함께 반영했습니다.");

    if (totalBalance.compareTo(new BigDecimal("1000000")) >= 0) {
      sentences.add("현재 계좌 잔액 규모는 비교적 안정적인 편입니다.");
    } else {
      sentences.add("현재 계좌 잔액 규모는 아직 크지 않은 편입니다.");
    }

    if (transactions.isEmpty()) {
      sentences.add("최근 카드 거래 데이터가 적어 거래 안정성 판단은 제한적으로 반영했습니다.");
    } else if (volatility.compareTo(new BigDecimal("0.50")) <= 0) {
      sentences.add("최근 3개월 소비 변동성이 낮아 거래 흐름은 비교적 안정적입니다.");
    } else {
      sentences.add("최근 3개월 소비 변동성이 커서 보수적으로 반영했습니다.");
    }

    return String.join(" ", sentences);
  }

  private String getGrade(int score) {
    if (score >= 900) {
      return "매우 우수";
    }
    if (score >= 800) {
      return "우수";
    }
    if (score >= 700) {
      return "양호";
    }
    return "보통";
  }

  private Integer findPreviousScore(Long memberId, Long latestId) {
    return creditHistoryRepository.findTop2ByMemberIdOrderByEvaluatedAtDescCreditHistoryIdDesc(memberId).stream()
        .filter(history -> !history.getCreditHistoryId().equals(latestId))
        .map(CreditHistory::getCreditScore)
        .findFirst()
        .orElse(null);
  }

  private CreditScoreResponse toResponse(CreditHistory history, Integer previousScore) {
    int creditScore = history.getCreditScore() == null ? 0 : history.getCreditScore();
    long estimatedLoanLimit = Math.max(5_000_000L, (long) creditScore * 50_000L);

    List<CreditScoreResponse.CreditFactorDto> factors = List.of(
        new CreditScoreResponse.CreditFactorDto(
            "자금 여력",
            creditScore >= 800 ? "양호" : "추가 확인 필요",
            "현재 계좌 잔액과 카드 거래 흐름을 바탕으로 내부 기준으로 계산한 결과입니다."
        ),
        new CreditScoreResponse.CreditFactorDto(
            "소비 흐름",
            creditScore >= 750 ? "안정적" : "변동성 있음",
            "최근 3개월 소비 흐름과 최근 거래일을 함께 반영합니다."
        ),
        new CreditScoreResponse.CreditFactorDto(
            "참고 한도",
            formatAmount(estimatedLoanLimit),
            "상품 조건과 추가 심사 정보에 따라 실제 한도는 달라질 수 있습니다."
        )
    );

    List<CreditScoreResponse.RecommendedLoanDto> recommendedLoans = List.of(
        new CreditScoreResponse.RecommendedLoanDto(
            "consumption-loan",
            "소비연동 자동상환 대출",
            "연 3.5%",
            "최대 5,000만원",
            "현재 내부 평가 점수와 최근 거래 흐름을 기준으로 예시 추천한 상품입니다."
        ),
        new CreditScoreResponse.RecommendedLoanDto(
            "youth-loan",
            "청년 주거안정 대출",
            "연 2.5%",
            "최대 3,000만원",
            "현재 자금 여력과 소비 흐름이 비교적 안정적인 구간으로 분석된 예시입니다."
        )
    );

    return new CreditScoreResponse(
        true,
        "OK",
        creditScore,
        history.getCreditGrade(),
        history.getEvaluationResult(),
        history.getEvaluatedAt() == null ? null : history.getEvaluatedAt().format(DATE_TIME_FORMATTER),
        previousScore == null ? null : creditScore - previousScore,
        estimatedLoanLimit,
        factors,
        recommendedLoans
    );
  }

  private String formatAmount(long value) {
    if (value >= 10_000L) {
      long man = value / 10_000L;
      return man + "만원";
    }
    return value + "원";
  }
}
