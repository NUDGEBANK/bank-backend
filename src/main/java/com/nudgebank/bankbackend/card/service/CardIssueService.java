package com.nudgebank.bankbackend.card.service;

import com.nudgebank.bankbackend.account.entity.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.auth.entity.User;
import com.nudgebank.bankbackend.auth.repository.UserRepository;
import com.nudgebank.bankbackend.card.dto.CardIssueRequest;
import com.nudgebank.bankbackend.card.dto.CardIssueResponse;
import com.nudgebank.bankbackend.card.entity.Card;
import com.nudgebank.bankbackend.card.repository.CardRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardIssueService {
  private static final DateTimeFormatter VALID_THRU_FORMATTER = DateTimeFormatter.ofPattern("MM/yy");
  private static final int MAX_NUMBER_RETRY = 100;

  private final UserRepository userRepository;
  private final AccountRepository accountRepository;
  private final CardRepository cardRepository;
  private final SecureRandom secureRandom = new SecureRandom();

  public CardIssueService(
      UserRepository userRepository,
      AccountRepository accountRepository,
      CardRepository cardRepository
  ) {
    this.userRepository = userRepository;
    this.accountRepository = accountRepository;
    this.cardRepository = cardRepository;
  }

  @Transactional
  public CardIssueResponse issue(Long userId, CardIssueRequest request) {
    if (userId == null) {
      throw new IllegalArgumentException("UNAUTHORIZED");
    }
    validateRequest(request);

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

    if (accountRepository.findByMemberId(userId).isPresent()) {
      throw new IllegalStateException("CARD_ALREADY_ISSUED");
    }

    OffsetDateTime now = OffsetDateTime.now();

    Account account = new Account();
    account.setMemberId(userId);
    account.setAccountName(user.getName());
    account.setAccountNumber(generateUniqueAccountNumber());
    account.setBalance(BigDecimal.ZERO.setScale(2));
    account.setOpenedAt(now);
    account.setProtectedBalance(0L);
    Account savedAccount = accountRepository.save(account);

    Card card = new Card();
    card.setAccountId(savedAccount.getId());
    card.setCardNumber(generateUniqueCardNumber());
    card.setIssuedAt(now);
    card.setValidThru(YearMonth.now().plusYears(5).format(VALID_THRU_FORMATTER));
    card.setPassword(request.cardPassword());
    card.setCvc(generateFixedDigits(3));
    card.setStatus("ACTIVE");
    Card savedCard = cardRepository.save(card);

    return new CardIssueResponse(
        true,
        "OK",
        savedAccount.getId(),
        savedAccount.getAccountName(),
        savedAccount.getAccountNumber(),
        savedAccount.getBalance(),
        savedCard.getId(),
        savedCard.getCardNumber(),
        savedCard.getValidThru(),
        savedCard.getCvc(),
        savedCard.getStatus()
    );
  }

  private void validateRequest(CardIssueRequest request) {
    if (request == null
        || isBlank(request.cardHolderName())
        || isBlank(request.phoneNumber())
        || isBlank(request.cardPassword())) {
      throw new IllegalArgumentException("MISSING_FIELDS");
    }

    if (!request.cardPassword().matches("\\d{4}")) {
      throw new IllegalArgumentException("INVALID_CARD_PASSWORD");
    }
  }

  private String generateUniqueAccountNumber() {
    for (int i = 0; i < MAX_NUMBER_RETRY; i++) {
      String candidate = generateFixedDigits(7) + "-" + generateFixedDigits(7);
      if (!accountRepository.existsByAccountNumber(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("ACCOUNT_NUMBER_GENERATION_FAILED");
  }

  private String generateUniqueCardNumber() {
    for (int i = 0; i < MAX_NUMBER_RETRY; i++) {
      String candidate = generateFixedDigits(4)
          + "-" + generateFixedDigits(4)
          + "-" + generateFixedDigits(4)
          + "-" + generateFixedDigits(4);
      if (!cardRepository.existsByCardNumber(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("CARD_NUMBER_GENERATION_FAILED");
  }

  private String generateFixedDigits(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      builder.append(secureRandom.nextInt(10));
    }
    return builder.toString();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
