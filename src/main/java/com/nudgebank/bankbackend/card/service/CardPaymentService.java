package com.nudgebank.bankbackend.card.service;

import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.card.domain.Card;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.card.domain.Market;
import com.nudgebank.bankbackend.card.domain.MarketCategory;
import com.nudgebank.bankbackend.card.dto.CardPaymentRequest;
import com.nudgebank.bankbackend.card.repository.CardRepository;
import com.nudgebank.bankbackend.card.repository.CardTransactionRepository;
import com.nudgebank.bankbackend.card.repository.MarketCategoryRepository;
import com.nudgebank.bankbackend.card.repository.MarketRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class CardPaymentService {

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final MarketRepository marketRepository;
    private final MarketCategoryRepository marketCategoryRepository;
    private final CardTransactionRepository cardTransactionRepository;

    public Long processPayment(CardPaymentRequest request) {
        validateRequest(request);

        Card card = cardRepository.findById(request.cardId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "카드를 찾을 수 없습니다. cardId=" + request.cardId()
                ));

        Account account = accountRepository.findByIdForUpdate(card.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "카드에 연결된 계좌를 찾을 수 없습니다. accountId=" + card.getAccountId()
                ));

        Market market = marketRepository.findById(request.marketId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "가맹점을 찾을 수 없습니다. marketId=" + request.marketId()
                ));

        MarketCategory category = marketCategoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "카테고리를 찾을 수 없습니다. categoryId=" + request.categoryId()
                ));

        BigDecimal balance = nullSafe(account.getBalance());
        BigDecimal protectedBalance = nullSafe(account.getProtectedBalance());
        BigDecimal availableBalance = balance.subtract(protectedBalance);

        if (availableBalance.compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }

        account.withdraw(request.amount());

        CardTransaction transaction = CardTransaction.builder()
                .card(card)
                .market(market)
                .category(category)
                .qrId(request.qrId())
                .amount(request.amount())
                .transactionDatetime(OffsetDateTime.now())
                .menuName(request.menuName())
                .quantity(request.quantity())
                .build();

        CardTransaction saved = cardTransactionRepository.save(transaction);
        return saved.getTransactionId();
    }

    private void validateRequest(CardPaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청값이 없습니다.");
        }
        if (request.cardId() == null) {
            throw new IllegalArgumentException("cardId는 필수입니다.");
        }
        if (request.marketId() == null) {
            throw new IllegalArgumentException("marketId는 필수입니다.");
        }
        if (request.categoryId() == null) {
            throw new IllegalArgumentException("categoryId는 필수입니다.");
        }
        if (request.qrId() == null || request.qrId().isBlank()) {
            throw new IllegalArgumentException("qrId는 필수입니다.");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount는 0보다 커야 합니다.");
        }
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}