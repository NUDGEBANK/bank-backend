package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.LoanRepaymentHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepaymentHistoryRepository extends JpaRepository<LoanRepaymentHistory, Long> {

    List<LoanRepaymentHistory> findTop10ByLoanHistory_IdOrderByRepaymentDatetimeDesc(Long loanHistoryId);

    List<LoanRepaymentHistory> findByTransaction_TransactionIdIn(List<Long> transactionIds);

    @Query("""
        select history
        from LoanRepaymentHistory history
        left join fetch history.transaction transaction
        left join fetch transaction.card
        left join fetch transaction.market
        left join fetch transaction.category
        where history.loanHistory.id = :loanHistoryId
        order by history.repaymentDatetime desc
        """)
    List<LoanRepaymentHistory> findByLoanHistoryIdWithTransaction(@Param("loanHistoryId") Long loanHistoryId, Pageable pageable);
}
