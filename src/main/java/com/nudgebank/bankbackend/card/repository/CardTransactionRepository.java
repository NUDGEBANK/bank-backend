package com.nudgebank.bankbackend.card.repository;

import com.nudgebank.bankbackend.card.domain.CardTransaction;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long> {
  @EntityGraph(attributePaths = {"market", "category"})
  List<CardTransaction> findByCardCardIdOrderByTransactionDatetimeDesc(Long cardId);

  List<CardTransaction> findByCardCardIdAndTransactionDatetimeBetween(
      Long cardId,
      OffsetDateTime start,
      OffsetDateTime end
  );
}
