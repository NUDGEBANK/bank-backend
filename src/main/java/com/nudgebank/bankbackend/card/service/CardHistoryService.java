package com.nudgebank.bankbackend.card.service;

import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.card.domain.Card;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.card.dto.CardHistoryResponse;
import com.nudgebank.bankbackend.card.repository.CardRepository;
import com.nudgebank.bankbackend.card.repository.CardTransactionRepository;
import com.nudgebank.bankbackend.loan.domain.LoanRepaymentHistory;
import com.nudgebank.bankbackend.loan.repository.LoanRepaymentHistoryRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CardHistoryService {
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final AccountRepository accountRepository;
  private final CardRepository cardRepository;
  private final CardTransactionRepository cardTransactionRepository;
  private final LoanRepaymentHistoryRepository loanRepaymentHistoryRepository;

  public CardHistoryService(
      AccountRepository accountRepository,
      CardRepository cardRepository,
      CardTransactionRepository cardTransactionRepository,
      LoanRepaymentHistoryRepository loanRepaymentHistoryRepository
  ) {
    this.accountRepository = accountRepository;
    this.cardRepository = cardRepository;
    this.cardTransactionRepository = cardTransactionRepository;
    this.loanRepaymentHistoryRepository = loanRepaymentHistoryRepository;
  }

  public CardHistoryResponse getHistory(Long userId) {
    if (userId == null) {
      throw new IllegalArgumentException("UNAUTHORIZED");
    }

    List<CardHistoryResponse.CardHistoryAccountDto> accounts = accountRepository.findAllByMemberId(userId).stream()
        .map(this::toAccountDto)
        .toList();

    return new CardHistoryResponse(true, "OK", accounts);
  }

  private CardHistoryResponse.CardHistoryAccountDto toAccountDto(Account account) {
    Optional<Card> cardOptional = cardRepository.findByAccountId(account.getAccountId());
    if (cardOptional.isEmpty()) {
      return new CardHistoryResponse.CardHistoryAccountDto(
          account.getAccountId(),
          account.getAccountName(),
          account.getAccountNumber(),
          account.getBalance(),
          null,
          null,
          null,
          null,
          BigDecimal.ZERO,
          List.of()
      );
    }

    Card card = cardOptional.get();
    List<CardTransaction> transactions =
        cardTransactionRepository.findByCardCardIdOrderByTransactionDatetimeDesc(card.getCardId());
    Map<Long, LoanRepaymentHistory> repaymentHistoryByTransactionId = loanRepaymentHistoryRepository
        .findByTransaction_TransactionIdIn(
            transactions.stream().map(CardTransaction::getTransactionId).toList()
        ).stream()
        .collect(Collectors.toMap(
            history -> history.getTransaction().getTransactionId(),
            Function.identity()
        ));

    return new CardHistoryResponse.CardHistoryAccountDto(
        account.getAccountId(),
        account.getAccountName(),
        account.getAccountNumber(),
        account.getBalance(),
        card.getCardId(),
        card.getCardNumber(),
        card.getExpiredYm(),
        card.getStatus(),
        calculateSpentThisMonth(card.getCardId()),
        transactions.stream().map(transaction -> toTransactionDto(
            transaction,
            repaymentHistoryByTransactionId.get(transaction.getTransactionId())
        )).toList()
    );
  }

  private CardHistoryResponse.CardHistoryTransactionDto toTransactionDto(
      CardTransaction transaction,
      LoanRepaymentHistory repaymentHistory
  ) {
    OffsetDateTime transactionDateTime = transaction.getTransactionDatetime();
    String formattedDateTime = transactionDateTime == null
        ? null
        : transactionDateTime.withOffsetSameInstant(ZoneOffset.ofHours(9)).format(DATE_TIME_FORMATTER);
    String formattedRepaymentDateTime = repaymentHistory == null || repaymentHistory.getRepaymentDatetime() == null
        ? null
        : repaymentHistory.getRepaymentDatetime().withOffsetSameInstant(ZoneOffset.ofHours(9)).format(DATE_TIME_FORMATTER);

    return new CardHistoryResponse.CardHistoryTransactionDto(
        transaction.getTransactionId(),
        transaction.getMarket().getMarketName(),
        transaction.getCategory().getCategoryName(),
        transaction.getAmount(),
        formattedDateTime,
        transaction.getMenuName(),
        transaction.getQuantity(),
        repaymentHistory != null,
        repaymentHistory != null ? repaymentHistory.getRepaymentRate() : BigDecimal.ZERO,
        repaymentHistory != null ? repaymentHistory.getRepaymentAmount() : BigDecimal.ZERO,
        formattedRepaymentDateTime,
        repaymentHistory != null ? repaymentHistory.getRemainingBalance() : null
    );
  }

  private BigDecimal calculateSpentThisMonth(Long cardId) {
    YearMonth now = YearMonth.now();
    OffsetDateTime start = now.atDay(1).atStartOfDay().atOffset(ZoneOffset.ofHours(9));
    OffsetDateTime end = now.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.ofHours(9)).minusNanos(1);

    return cardTransactionRepository.findByCardCardIdAndTransactionDatetimeBetween(cardId, start, end).stream()
        .map(CardTransaction::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
