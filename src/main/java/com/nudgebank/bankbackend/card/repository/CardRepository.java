package com.nudgebank.bankbackend.card.repository;

import com.nudgebank.bankbackend.card.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {
  boolean existsByCardNumber(String cardNumber);
  Optional<Card> findByAccountId(Long accountId);
}
