package com.nudgebank.bankbackend.card.service;

import com.nudgebank.bankbackend.card.dto.CardHistoryResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class CardHistoryService {
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final JdbcTemplate jdbcTemplate;

  public CardHistoryService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public CardHistoryResponse getHistory(Long userId) {
    if (userId == null) {
      throw new IllegalArgumentException("UNAUTHORIZED");
    }

    List<CardHistoryResponse.CardHistoryAccountDto> accounts = findAccounts(userId);
    return new CardHistoryResponse(true, "OK", accounts);
  }

  private List<CardHistoryResponse.CardHistoryAccountDto> findAccounts(Long userId) {
    String accountMemberColumn = resolveColumnName("account", "member_id", "memberid");
    String cardAccountColumn = resolveColumnName("card", "account_id", "accountid");

    return jdbcTemplate.query(
        """
        select
          a.account_id,
          a.account_name,
          a.account_number,
          a.balance,
          c.card_id,
          c.card_number,
          c.status
        from account a
        left join card c on c.%s = a.account_id
        where a.%s = ?
        order by a.account_id desc
        """.formatted(cardAccountColumn, accountMemberColumn),
        (rs, rowNum) -> toAccountDto(rs),
        userId
    );
  }

  private CardHistoryResponse.CardHistoryAccountDto toAccountDto(ResultSet rs) throws SQLException {
    Long accountId = rs.getLong("account_id");
    Long cardId = getNullableLong(rs, "card_id");

    return new CardHistoryResponse.CardHistoryAccountDto(
        accountId,
        rs.getString("account_name"),
        rs.getString("account_number"),
        rs.getBigDecimal("balance"),
        cardId,
        rs.getString("card_number"),
        rs.getString("status"),
        cardId == null ? BigDecimal.ZERO : calculateSpentThisMonth(cardId),
        cardId == null ? List.of() : findTransactions(cardId)
    );
  }

  private List<CardHistoryResponse.CardHistoryTransactionDto> findTransactions(Long cardId) {
    String transactionCardColumn = resolveColumnName("card_transaction", "card_id", "cardid");
    String transactionMarketColumn = resolveColumnName("card_transaction", "market_id", "marketid");
    String transactionCategoryColumn = resolveColumnName("card_transaction", "category_id", "categoryid");
    String transactionQrColumn = resolveColumnName("card_transaction", "qr_id", "qrid");
    String marketCategoryColumn = resolveColumnName("market", "category_id", "categoryid");

    return jdbcTemplate.query(
        """
        select
          ct.transaction_id,
          m.market_name,
          mc.category_name,
          ct.amount,
          ct.transaction_datetime,
          ct.menu_name,
          ct.quantity,
          ct.%s
        from card_transaction ct
        join market m on m.market_id = ct.%s
        join market_category mc on mc.category_id = ct.%s
        where ct.%s = ?
        order by ct.transaction_datetime desc
        """.formatted(transactionQrColumn, transactionMarketColumn, transactionCategoryColumn, transactionCardColumn),
        transactionRowMapper(),
        cardId
    );
  }

  private RowMapper<CardHistoryResponse.CardHistoryTransactionDto> transactionRowMapper() {
    return (rs, rowNum) -> {
      Timestamp timestamp = rs.getTimestamp("transaction_datetime");
      String formattedDateTime = timestamp == null
          ? null
          : timestamp.toInstant().atOffset(ZoneOffset.ofHours(9)).format(DATE_TIME_FORMATTER);

      return new CardHistoryResponse.CardHistoryTransactionDto(
          rs.getLong("transaction_id"),
          rs.getString("market_name"),
          rs.getString("category_name"),
          rs.getBigDecimal("amount"),
          formattedDateTime,
          rs.getString("menu_name"),
          getNullableInteger(rs, "quantity")
      );
    };
  }

  private BigDecimal calculateSpentThisMonth(Long cardId) {
    String transactionCardColumn = resolveColumnName("card_transaction", "card_id", "cardid");

    YearMonth now = YearMonth.now();
    OffsetDateTime start = now.atDay(1).atStartOfDay().atOffset(ZoneOffset.ofHours(9));
    OffsetDateTime end = now.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.ofHours(9)).minusNanos(1);

    BigDecimal value = jdbcTemplate.queryForObject(
        """
        select coalesce(sum(amount), 0)
        from card_transaction
        where %s = ?
          and transaction_datetime between ? and ?
        """.formatted(transactionCardColumn),
        BigDecimal.class,
        cardId,
        Timestamp.from(start.toInstant()),
        Timestamp.from(end.toInstant())
    );

    return value == null ? BigDecimal.ZERO : value;
  }

  private String resolveColumnName(String tableName, String preferred, String fallback) {
    Integer preferredCount = jdbcTemplate.queryForObject(
        """
        select count(*)
        from information_schema.columns
        where table_schema = 'public' and table_name = ? and column_name = ?
        """,
        Integer.class,
        tableName,
        preferred
    );

    if (preferredCount != null && preferredCount > 0) {
      return preferred;
    }

    return fallback;
  }

  private Long getNullableLong(ResultSet rs, String columnName) throws SQLException {
    long value = rs.getLong(columnName);
    return rs.wasNull() ? null : value;
  }

  private Integer getNullableInteger(ResultSet rs, String columnName) throws SQLException {
    int value = rs.getInt(columnName);
    return rs.wasNull() ? null : value;
  }
}
