package com.nudgebank.bankbackend.card.repository;

import com.nudgebank.bankbackend.card.entity.Card;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, Long> {
  boolean existsByCardNumber(String cardNumber);
  Optional<Card> findByAccountId(Long accountId);
}
