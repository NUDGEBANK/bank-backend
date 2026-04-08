package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.LoanRepaymentHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepaymentHistoryRepository extends JpaRepository<LoanRepaymentHistory, Long> {

    List<LoanRepaymentHistory> findTop10ByLoanHistory_IdOrderByRepaymentDatetimeDesc(Long loanHistoryId);
}
