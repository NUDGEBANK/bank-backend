package com.nudgebank.bankbackend.card.repository;

import com.nudgebank.bankbackend.card.domain.CardTransaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long> {
  @EntityGraph(attributePaths = {"market", "category"})
  List<CardTransaction> findByCardCardIdOrderByTransactionDatetimeDesc(Long cardId);

  List<CardTransaction> findByCardCardIdAndTransactionDatetimeBetween(
      Long cardId,
      OffsetDateTime start,
      OffsetDateTime end
  );

  @Query("""
        select coalesce(sum(ct.amount), 0)
        from CardTransaction ct
        where ct.card.cardId = :cardId
          and ct.transactionDatetime >= :startOfMonth
          and ct.transactionDatetime < :startOfNextMonth
          and (
                ct.transactionDatetime < :transactionDatetime
                or (
                    ct.transactionDatetime = :transactionDatetime
                    and ct.transactionId <= :transactionId
                )
              )
    """)
  BigDecimal sumCurrentMonthSpendingAmountUntilTransaction(
          @Param("cardId") Long cardId,
          @Param("startOfMonth") OffsetDateTime startOfMonth,
          @Param("startOfNextMonth") OffsetDateTime startOfNextMonth,
          @Param("transactionDatetime") OffsetDateTime transactionDatetime,
          @Param("transactionId") Long transactionId
  );
}
