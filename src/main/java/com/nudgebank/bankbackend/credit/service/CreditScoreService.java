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
import com.nudgebank.bankbackend.deposit.domain.DepositAccount;
import com.nudgebank.bankbackend.deposit.domain.DepositPaymentSchedule;
import com.nudgebank.bankbackend.deposit.repository.DepositAccountRepository;
import com.nudgebank.bankbackend.deposit.repository.DepositPaymentScheduleRepository;
import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import com.nudgebank.bankbackend.loan.domain.RepaymentSchedule;
import com.nudgebank.bankbackend.loan.repository.LoanHistoryRepository;
import com.nudgebank.bankbackend.loan.repository.RepaymentScheduleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
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
  private static final double BASE_SCORE = 580;
  private static final double STARTER_PROFILE_TARGET_SCORE = 500;
  private static final int EARLY_STAGE_MIN_SCORE = 500;
  private static final int EARLY_STAGE_MIN_TRANSACTION_COUNT = 6;
  private static final String LOAN_STATUS_OVERDUE = "OVERDUE";
  private static final String LOAN_STATUS_COMPLETED = "COMPLETED";
  private static final String DEPOSIT_STATUS_ACTIVE = "ACTIVE";
  private static final String DEPOSIT_STATUS_CLOSED = "CLOSED";
  private static final String DEPOSIT_STATUS_EARLY_CLOSED = "EARLY_CLOSED";
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

  private final MemberRepository memberRepository;
  private final AccountRepository accountRepository;
  private final CardRepository cardRepository;
  private final CardTransactionRepository cardTransactionRepository;
  private final CreditHistoryRepository creditHistoryRepository;
  private final LoanHistoryRepository loanHistoryRepository;
  private final RepaymentScheduleRepository repaymentScheduleRepository;
  private final DepositAccountRepository depositAccountRepository;
  private final DepositPaymentScheduleRepository depositPaymentScheduleRepository;

  public CreditScoreService(
      MemberRepository memberRepository,
      AccountRepository accountRepository,
      CardRepository cardRepository,
      CardTransactionRepository cardTransactionRepository,
      CreditHistoryRepository creditHistoryRepository,
      LoanHistoryRepository loanHistoryRepository,
      RepaymentScheduleRepository repaymentScheduleRepository,
      DepositAccountRepository depositAccountRepository,
      DepositPaymentScheduleRepository depositPaymentScheduleRepository
  ) {
    this.memberRepository = memberRepository;
    this.accountRepository = accountRepository;
    this.cardRepository = cardRepository;
    this.cardTransactionRepository = cardTransactionRepository;
    this.creditHistoryRepository = creditHistoryRepository;
    this.loanHistoryRepository = loanHistoryRepository;
    this.repaymentScheduleRepository = repaymentScheduleRepository;
    this.depositAccountRepository = depositAccountRepository;
    this.depositPaymentScheduleRepository = depositPaymentScheduleRepository;
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

    List<LoanHistory> loanHistories = loanHistoryRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId);
    List<RepaymentSchedule> repaymentSchedules =
        repaymentScheduleRepository.findAllByLoanHistory_Member_MemberIdOrderByDueDateAsc(memberId);
    List<DepositAccount> depositAccounts = depositAccountRepository.findAllByMemberId(memberId);
    List<DepositPaymentSchedule> depositSchedules =
        depositPaymentScheduleRepository.findAllByDepositAccount_MemberIdOrderByDueDateAsc(memberId);

    CreditMetrics metrics = buildCreditMetrics(
        accounts,
        cards,
        transactions,
        loanHistories,
        repaymentSchedules,
        depositAccounts,
        depositSchedules
    );

    int score = calculateScore(metrics);
    String grade = getGrade(score);
    String summary = buildSummary(metrics);

    CreditHistory creditHistory = CreditHistory.create(
        memberId,
        score,
        grade,
        summary,
        LocalDateTime.now()
    );

    return creditHistoryRepository.save(creditHistory);
  }

  private CreditMetrics buildCreditMetrics(
      List<Account> accounts,
      List<Card> cards,
      List<CardTransaction> transactions,
      List<LoanHistory> loanHistories,
      List<RepaymentSchedule> repaymentSchedules,
      List<DepositAccount> depositAccounts,
      List<DepositPaymentSchedule> depositSchedules
  ) {
    BigDecimal accountBalance = sumAccounts(accounts);
    BigDecimal depositBalance = sumDepositBalance(depositAccounts);
    BigDecimal totalLoanRemaining = sumRemainingPrincipal(loanHistories);
    BigDecimal netAsset = scale(accountBalance.add(depositBalance).subtract(totalLoanRemaining)).max(ZERO);

    Map<YearMonth, BigDecimal> monthlyTotals = buildMonthlyTotals(transactions, 3);
    BigDecimal averageMonthly = average(monthlyTotals.values().stream().toList());
    Map<YearMonth, Integer> monthlyTransactionCounts = buildMonthlyTransactionCounts(transactions, 3);
    int activeMonths = (int) monthlyTransactionCounts.values().stream()
        .filter(count -> count >= 2)
        .count();
    int recentTransactionCount = monthlyTransactionCounts.values().stream()
        .mapToInt(Integer::intValue)
        .sum();
    BigDecimal volatility = calculateVolatility(monthlyTotals, averageMonthly);
    long daysSinceLatestTransaction = calculateDaysSinceLatestTransaction(transactions);

    LoanMetrics loanMetrics = buildLoanMetrics(loanHistories, repaymentSchedules);
    DepositMetrics depositMetrics = buildDepositMetrics(depositAccounts, depositSchedules);

    return new CreditMetrics(
        accountBalance,
        depositBalance,
        totalLoanRemaining,
        netAsset,
        averageMonthly,
        monthlyTotals,
        activeMonths,
        recentTransactionCount,
        volatility,
        daysSinceLatestTransaction,
        cards.isEmpty(),
        transactions.isEmpty(),
        loanMetrics,
        depositMetrics
    );
  }

  private int calculateScore(CreditMetrics metrics) {
    double score = BASE_SCORE;

    score += calculateNetAssetScore(metrics.netAsset().doubleValue());
    score += scaleScore(metrics.activeMonths(), 0, 3, 42);
    score += calculateSpendingBurdenScore(metrics.netAsset().doubleValue(), metrics.averageMonthlySpending().doubleValue());
    score += calculateVolatilityScore(metrics.noTransactions(), metrics.volatility().doubleValue());
    score += scaleScore(metrics.recentTransactionCount(), 0, 30, 28);
    score += calculateRecencyScore(metrics.daysSinceLatestTransaction(), metrics.noTransactions());
    score += calculateLoanBurdenScore(
        metrics.totalLoanRemaining().doubleValue(),
        metrics.accountBalance().add(metrics.depositBalance()).doubleValue()
    );
    score += calculateLoanRepaymentScore(metrics.loanMetrics());
    score += calculateDepositHabitScore(metrics.depositMetrics());
    score += calculateInitialProfileScore(metrics);

    if (metrics.activeMonths() == 0 || metrics.recentTransactionCount() < 3) {
      score -= 20;
    }

    score += calculateStarterProfileAdjustment(metrics, score);

    int roundedScore = (int) Math.round(score);
    int boundedScore = Math.max(300, Math.min(950, roundedScore));

    if (shouldProtectEarlyStageScore(metrics)) {
      return Math.max(EARLY_STAGE_MIN_SCORE, boundedScore);
    }

    return boundedScore;
  }

  private Map<YearMonth, BigDecimal> buildMonthlyTotals(List<CardTransaction> transactions, int months) {
    OffsetDateTime cutoff = OffsetDateTime.now().minusMonths(months);
    Map<YearMonth, BigDecimal> monthlyTotals = new LinkedHashMap<>();

    for (CardTransaction transaction : transactions) {
      if (transaction.getTransactionDatetime() == null || transaction.getTransactionDatetime().isBefore(cutoff)) {
        continue;
      }

      YearMonth yearMonth = YearMonth.from(transaction.getTransactionDatetime());
      monthlyTotals.merge(yearMonth, nullSafe(transaction.getAmount()), BigDecimal::add);
    }

    return monthlyTotals;
  }

  private BigDecimal average(List<BigDecimal> values) {
    if (values.isEmpty()) {
      return ZERO;
    }

    BigDecimal sum = values.stream().reduce(ZERO, BigDecimal::add);
    return scale(sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP));
  }

  private BigDecimal calculateVolatility(Map<YearMonth, BigDecimal> monthlyTotals, BigDecimal averageMonthly) {
    if (monthlyTotals.size() < 2 || averageMonthly.compareTo(BigDecimal.ZERO) == 0) {
      return ZERO;
    }

    BigDecimal max = monthlyTotals.values().stream().max(BigDecimal::compareTo).orElse(ZERO);
    BigDecimal min = monthlyTotals.values().stream().min(BigDecimal::compareTo).orElse(ZERO);
    return scale(max.subtract(min).divide(averageMonthly, 2, RoundingMode.HALF_UP));
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

  private double calculateNetAssetScore(double netAssetValue) {
    if (netAssetValue <= 0) {
      return 0;
    }
    if (netAssetValue <= 5_000_000) {
      return scaleScore(netAssetValue, 0, 5_000_000, 18);
    }
    if (netAssetValue <= 20_000_000) {
      return 18 + scaleScore(netAssetValue - 5_000_000, 0, 15_000_000, 17);
    }
    if (netAssetValue <= 50_000_000) {
      return 35 + scaleScore(netAssetValue - 20_000_000, 0, 30_000_000, 15);
    }
    return 50;
  }

  private double calculateSpendingBurdenScore(double netAssetValue, double averageMonthlyValue) {
    if (averageMonthlyValue <= 0) {
      return 0;
    }

    double referenceAsset = Math.max(netAssetValue, 300_000);
    double burdenRatio = averageMonthlyValue / referenceAsset;

    if (burdenRatio <= 0.15) {
      return 35;
    }
    if (burdenRatio <= 0.30) {
      return 35 - scaleScore(burdenRatio - 0.15, 0, 0.15, 14);
    }
    if (burdenRatio <= 0.60) {
      return 21 - scaleScore(burdenRatio - 0.30, 0, 0.30, 22);
    }
    if (burdenRatio <= 1.00) {
      return -1 - scaleScore(burdenRatio - 0.60, 0, 0.40, 16);
    }
    return -17 - scaleScore(Math.min(burdenRatio - 1.00, 1.50), 0, 1.50, 8);
  }

  private double calculateVolatilityScore(boolean noTransactions, double volatility) {
    if (noTransactions) {
      return 0;
    }
    if (volatility <= 0) {
      return 0;
    }
    if (volatility <= 0.20) {
      return 36;
    }
    if (volatility <= 0.40) {
      return 36 - scaleScore(volatility - 0.20, 0, 0.20, 12);
    }
    if (volatility <= 0.80) {
      return 24 - scaleScore(volatility - 0.40, 0, 0.40, 24);
    }
    return -scaleScore(Math.min(volatility - 0.80, 1.20), 0, 1.20, 18);
  }

  private double calculateRecencyScore(long daysSinceLatest, boolean noTransactions) {
    if (noTransactions || daysSinceLatest < 0) {
      return -18;
    }
    if (daysSinceLatest <= 14) {
      return 18;
    }
    if (daysSinceLatest <= 30) {
      return 12;
    }
    if (daysSinceLatest <= 60) {
      return 0;
    }
    if (daysSinceLatest <= 90) {
      return -8;
    }
    return -18;
  }

  private double calculateLoanBurdenScore(double remainingPrincipalValue, double grossAssetValue) {
    if (remainingPrincipalValue <= 0) {
      return 3;
    }

    double referenceAsset = Math.max(grossAssetValue, 500_000);
    double debtRatio = remainingPrincipalValue / referenceAsset;

    if (debtRatio <= 0.30) {
      return 40 - scaleScore(debtRatio, 0, 0.30, 14);
    }
    if (debtRatio <= 1.00) {
      return 26 - scaleScore(debtRatio - 0.30, 0, 0.70, 24);
    }
    if (debtRatio <= 2.00) {
      return -scaleScore(debtRatio - 1.00, 0, 1.00, 18);
    }
    return -18 - scaleScore(Math.min(debtRatio - 2.00, 2.00), 0, 2.00, 12);
  }

  private double calculateLoanRepaymentScore(LoanMetrics metrics) {
    if (metrics.overdueLoanCount() > 0) {
      return -60;
    }
    if (metrics.dueScheduleCount() <= 0) {
      return metrics.loanCount() > 0 ? 8 : 0;
    }

    double overdueRatio = metrics.overdueDueCount() / (double) metrics.dueScheduleCount();
    if (metrics.overdueDueCount() == 0 && metrics.maxOverdueDays() == 0) {
      return 34;
    }
    if (overdueRatio <= 0.10 && metrics.maxOverdueDays() <= 7) {
      return 12;
    }
    if (overdueRatio <= 0.25 && metrics.maxOverdueDays() <= 30) {
      return -8;
    }
    if (overdueRatio <= 0.50) {
      return -28;
    }
    return -60;
  }

  private double calculateDepositHabitScore(DepositMetrics metrics) {
    if (metrics.depositAccountCount() == 0) {
      return 0;
    }

    double score = 0;
    if (metrics.activeDepositCount() > 0) {
      score += 7;
    }
    if (metrics.maturityClosedCount() > 0) {
      score += 10;
    }
    if (metrics.dueScheduleCount() > 0) {
      double paidRatio = metrics.paidDueCount() / (double) metrics.dueScheduleCount();
      if (paidRatio >= 0.95) {
        score += 10;
      } else if (paidRatio >= 0.75) {
        score += 7;
      } else if (paidRatio >= 0.50) {
        score += 4;
      } else {
        score -= 7;
      }
    }

    score -= Math.min(metrics.earlyClosedCount() * 7.0, 14.0);
    return Math.max(-14, Math.min(27, score));
  }

  private double calculateInitialProfileScore(CreditMetrics metrics) {
    boolean noAccounts = metrics.accountBalance().compareTo(BigDecimal.ZERO) <= 0;
    boolean noLoans = metrics.loanMetrics().loanCount() == 0;
    boolean noDeposits = metrics.depositMetrics().depositAccountCount() == 0;
    boolean noCardsOrTransactions = metrics.noCards() || metrics.noTransactions();

    if (noAccounts && noLoans && noDeposits && noCardsOrTransactions) {
      return -42;
    }
    return 0;
  }

  private double calculateStarterProfileAdjustment(CreditMetrics metrics, double currentScore) {
    if (!isStarterProfile(metrics)) {
      return 0;
    }
    return STARTER_PROFILE_TARGET_SCORE - currentScore;
  }

  private boolean isStarterProfile(CreditMetrics metrics) {
    boolean noLoans = metrics.loanMetrics().loanCount() == 0;
    boolean noDeposits = metrics.depositMetrics().depositAccountCount() == 0;
    return metrics.noCards() && metrics.noTransactions() && noLoans && noDeposits;
  }

  private boolean shouldProtectEarlyStageScore(CreditMetrics metrics) {
    if (isStarterProfile(metrics)) {
      return true;
    }

    boolean noLoans = metrics.loanMetrics().loanCount() == 0;
    boolean noDeposits = metrics.depositMetrics().depositAccountCount() == 0;
    boolean lowTransactionVolume = metrics.recentTransactionCount() < EARLY_STAGE_MIN_TRANSACTION_COUNT;
    boolean noActiveMonths = metrics.activeMonths() == 0;

    return noLoans && noDeposits && lowTransactionVolume && noActiveMonths;
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

  private String buildSummary(CreditMetrics metrics) {
    List<String> sentences = new ArrayList<>();

    if (metrics.netAsset().compareTo(new BigDecimal("1000000")) >= 0) {
      sentences.add("순자산 기준 자금 여력은 비교적 안정적인 편입니다.");
    } else {
      sentences.add("순자산 규모는 아직 크지 않아 보수적으로 반영했습니다.");
    }

    if (metrics.loanMetrics().loanCount() == 0) {
      sentences.add("현재 대출 잔액이 없어 부채 부담은 낮은 편으로 반영했습니다.");
    } else if (metrics.loanMetrics().overdueLoanCount() > 0 || metrics.loanMetrics().overdueDueCount() > 0) {
      sentences.add("대출 상환 일정 중 연체 이력이 확인되어 신용 점수에 보수적으로 반영했습니다.");
    } else {
      sentences.add("대출 잔여원금과 상환 일정은 전반적으로 안정적으로 반영했습니다.");
    }

    if (metrics.depositMetrics().depositAccountCount() == 0) {
      sentences.add("예적금 이력이 없어 저축 습관 평가는 중립적으로 반영했습니다.");
    } else if (metrics.depositMetrics().maturityClosedCount() > 0) {
      sentences.add("만기 유지한 예적금 이력이 있어 금융 습관 안정성에 가산점을 반영했습니다.");
    } else if (metrics.depositMetrics().earlyClosedCount() > 0) {
      sentences.add("중도해지 이력이 있어 예적금 유지 성실도는 보수적으로 반영했습니다.");
    } else {
      sentences.add("예적금 유지 및 납입 흐름이 확인되어 저축 습관을 함께 반영했습니다.");
    }

    if (metrics.noTransactions()) {
      sentences.add("최근 카드 거래 데이터가 적어 소비 안정성 평가는 제한적으로 반영했습니다.");
    } else if (metrics.volatility().compareTo(new BigDecimal("0.50")) <= 0) {
      sentences.add("최근 3개월 소비 변동성은 비교적 안정적인 편입니다.");
    } else {
      sentences.add("최근 3개월 소비 변동성이 커서 소비 흐름은 보수적으로 반영했습니다.");
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
            "순자산",
            creditScore >= 800 ? "양호" : "추가 확인 필요",
            "입출금 계좌와 예적금 잔액에서 대출 잔여원금을 차감한 순자산 기준을 반영합니다."
        ),
        new CreditScoreResponse.CreditFactorDto(
            "대출 상환",
            creditScore >= 750 ? "안정적" : "보수적 반영",
            "대출 잔여원금 규모와 상환 일정의 연체 여부를 함께 반영합니다."
        ),
        new CreditScoreResponse.CreditFactorDto(
            "저축·소비 습관",
            creditScore >= 700 ? "참고 가능" : "추가 관찰 필요",
            "최근 3개월 소비 흐름과 예적금 유지·납입 성실도를 함께 반영합니다."
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
            "현재 내부 평가 점수와 최근 상환·거래 흐름을 기준으로 예시 추천한 상품입니다."
        ),
        new CreditScoreResponse.RecommendedLoanDto(
            "youth-loan",
            "청년 주거안정 대출",
            "연 2.5%",
            "최대 3,000만원",
            "순자산과 상환 안정성, 예적금 유지 흐름을 함께 반영한 예시 추천입니다."
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

  private BigDecimal sumAccounts(List<Account> accounts) {
    return scale(accounts.stream()
        .map(Account::getBalance)
        .filter(balance -> balance != null)
        .reduce(ZERO, BigDecimal::add));
  }

  private BigDecimal sumDepositBalance(List<DepositAccount> depositAccounts) {
    return scale(depositAccounts.stream()
        .map(DepositAccount::getCurrentBalance)
        .filter(balance -> balance != null)
        .reduce(ZERO, BigDecimal::add));
  }

  private BigDecimal sumRemainingPrincipal(List<LoanHistory> loanHistories) {
    return scale(loanHistories.stream()
        .map(LoanHistory::getRemainingPrincipal)
        .filter(balance -> balance != null)
        .reduce(ZERO, BigDecimal::add));
  }

  private long calculateDaysSinceLatestTransaction(List<CardTransaction> transactions) {
    OffsetDateTime latestTransactionAt = transactions.stream()
        .map(CardTransaction::getTransactionDatetime)
        .filter(transactionDatetime -> transactionDatetime != null)
        .max(Comparator.naturalOrder())
        .orElse(null);

    if (latestTransactionAt == null) {
      return -1;
    }
    return Duration.between(latestTransactionAt, OffsetDateTime.now()).toDays();
  }

  private LoanMetrics buildLoanMetrics(List<LoanHistory> loanHistories, List<RepaymentSchedule> repaymentSchedules) {
    LocalDate today = LocalDate.now();
    int loanCount = (int) loanHistories.stream()
        .filter(history -> nullSafe(history.getRemainingPrincipal()).compareTo(BigDecimal.ZERO) > 0)
        .count();
    int overdueLoanCount = (int) loanHistories.stream()
        .filter(history -> LOAN_STATUS_OVERDUE.equalsIgnoreCase(history.getStatus()))
        .count();

    List<RepaymentSchedule> dueSchedules = repaymentSchedules.stream()
        .filter(schedule -> schedule.getDueDate() != null && !schedule.getDueDate().isAfter(today))
        .toList();

    int settledDueCount = (int) dueSchedules.stream()
        .filter(schedule -> Boolean.TRUE.equals(schedule.getIsSettled()))
        .count();
    int overdueDueCount = (int) dueSchedules.stream()
        .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()) || resolveOverdueDays(schedule) > 0)
        .count();
    int maxOverdueDays = dueSchedules.stream()
        .mapToInt(this::resolveOverdueDays)
        .max()
        .orElse(0);

    return new LoanMetrics(loanCount, overdueLoanCount, dueSchedules.size(), settledDueCount, overdueDueCount, maxOverdueDays);
  }

  private DepositMetrics buildDepositMetrics(
      List<DepositAccount> depositAccounts,
      List<DepositPaymentSchedule> depositSchedules
  ) {
    LocalDate today = LocalDate.now();
    int activeDepositCount = (int) depositAccounts.stream()
        .filter(account -> DEPOSIT_STATUS_ACTIVE.equalsIgnoreCase(account.getStatus()))
        .count();
    int maturityClosedCount = (int) depositAccounts.stream()
        .filter(account -> DEPOSIT_STATUS_CLOSED.equalsIgnoreCase(account.getStatus()))
        .count();
    int earlyClosedCount = (int) depositAccounts.stream()
        .filter(account -> DEPOSIT_STATUS_EARLY_CLOSED.equalsIgnoreCase(account.getStatus()))
        .count();

    List<DepositPaymentSchedule> dueSchedules = depositSchedules.stream()
        .filter(schedule -> schedule.getDueDate() != null && !schedule.getDueDate().isAfter(today))
        .toList();
    int paidDueCount = (int) dueSchedules.stream()
        .filter(schedule -> Boolean.TRUE.equals(schedule.getIsPaid()))
        .count();

    return new DepositMetrics(
        depositAccounts.size(),
        activeDepositCount,
        maturityClosedCount,
        earlyClosedCount,
        dueSchedules.size(),
        paidDueCount
    );
  }

  private int resolveOverdueDays(RepaymentSchedule schedule) {
    if (schedule.getOverdueDays() != null) {
      return Math.max(schedule.getOverdueDays(), 0);
    }
    if (!Boolean.TRUE.equals(schedule.getIsSettled()) && schedule.getDueDate() != null && schedule.getDueDate().isBefore(LocalDate.now())) {
      return (int) Duration.between(
          schedule.getDueDate().atStartOfDay(),
          LocalDate.now().atStartOfDay()
      ).toDays();
    }
    return 0;
  }

  private BigDecimal nullSafe(BigDecimal value) {
    return value != null ? scale(value) : ZERO;
  }

  private BigDecimal scale(BigDecimal value) {
    return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
  }

  private record CreditMetrics(
      BigDecimal accountBalance,
      BigDecimal depositBalance,
      BigDecimal totalLoanRemaining,
      BigDecimal netAsset,
      BigDecimal averageMonthlySpending,
      Map<YearMonth, BigDecimal> monthlyTotals,
      int activeMonths,
      int recentTransactionCount,
      BigDecimal volatility,
      long daysSinceLatestTransaction,
      boolean noCards,
      boolean noTransactions,
      LoanMetrics loanMetrics,
      DepositMetrics depositMetrics
  ) {
  }

  private record LoanMetrics(
      int loanCount,
      int overdueLoanCount,
      int dueScheduleCount,
      int settledDueCount,
      int overdueDueCount,
      int maxOverdueDays
  ) {
  }

  private record DepositMetrics(
      int depositAccountCount,
      int activeDepositCount,
      int maturityClosedCount,
      int earlyClosedCount,
      int dueScheduleCount,
      int paidDueCount
  ) {
  }
}
