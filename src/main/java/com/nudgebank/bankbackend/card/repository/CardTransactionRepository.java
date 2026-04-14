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
  boolean existsByQrId(String qrId);

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

    @EntityGraph(attributePaths = {"market", "category", "card"})
    @Query("""
    select ct
    from CardTransaction ct
    join ct.card c
    join Account a on a.accountId = c.accountId
    where a.memberId = :memberId
      and ct.transactionDatetime >= :start
      and ct.transactionDatetime < :end
    order by ct.transactionDatetime asc
""")
    List<CardTransaction> findByMemberIdAndTransactionDatetimeGreaterThanEqualAndTransactionDatetimeLessThan(
            @Param("memberId") Long memberId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
    select min(ct.transactionDatetime)
    from CardTransaction ct
    join ct.card c
    join Account a on a.accountId = c.accountId
    where a.memberId = :memberId
""")
    OffsetDateTime findFirstTransactionDatetimeByMemberId(@Param("memberId") Long memberId);
}
