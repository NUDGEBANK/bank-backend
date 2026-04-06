package com.nudgebank.bankbackend.card.service;

import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.card.dto.CardIssueRequest;
import com.nudgebank.bankbackend.card.dto.CardIssueResponse;
import com.nudgebank.bankbackend.card.domain.Card;
import com.nudgebank.bankbackend.card.repository.CardRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardIssueService {
  private static final DateTimeFormatter VALID_THRU_FORMATTER = DateTimeFormatter.ofPattern("MM/yy");
  private static final int MAX_NUMBER_RETRY = 100;

  private final MemberRepository memberRepository;
  private final AccountRepository accountRepository;
  private final CardRepository cardRepository;
  private final PasswordEncoder passwordEncoder;
  private final SecureRandom secureRandom = new SecureRandom();

  public CardIssueService(
      MemberRepository memberRepository,
      AccountRepository accountRepository,
      CardRepository cardRepository,
      PasswordEncoder passwordEncoder
  ) {
    this.memberRepository = memberRepository;
    this.accountRepository = accountRepository;
    this.cardRepository = cardRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public CardIssueResponse issue(Long userId, CardIssueRequest request) {
    if (userId == null) {
      throw new IllegalArgumentException("UNAUTHORIZED");
    }
    validateRequest(request);

    Member member = memberRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

    OffsetDateTime now = OffsetDateTime.now();

    Account account = accountRepository.save(
        Account.create(
            userId,
            request.accountName(),
            generateUniqueAccountNumber(),
            BigDecimal.ZERO.setScale(2),
            now,
            BigDecimal.ZERO.setScale(2)
        )
    );

    Card card = cardRepository.save(
        Card.create(
            account.getAccountId(),
            generateUniqueCardNumber(),
            now,
            YearMonth.now().plusYears(5).format(VALID_THRU_FORMATTER),
            passwordEncoder.encode(request.cardPassword()),
            generateFixedDigits(3),
            "ACTIVE"
        )
    );

    return new CardIssueResponse(
        true,
        "OK",
        account.getAccountId(),
        account.getAccountName(),
        account.getAccountNumber(),
        account.getBalance(),
        card.getCardId(),
        card.getCardNumber(),
        card.getExpiredYm(),
        card.getCvc(),
        card.getStatus()
    );
  }

  private void validateRequest(CardIssueRequest request) {
    if (request == null
        || isBlank(request.accountName())
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
