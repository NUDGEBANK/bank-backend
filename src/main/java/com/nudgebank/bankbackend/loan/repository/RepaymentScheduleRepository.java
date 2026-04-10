package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.RepaymentSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepaymentScheduleRepository extends JpaRepository<RepaymentSchedule, Long> {

    List<RepaymentSchedule> findAllByLoanHistory_IdOrderByDueDateAsc(Long loanHistoryId);

    List<RepaymentSchedule> findAllByLoanHistory_IdAndIsSettledFalseOrderByDueDateAsc(Long loanHistoryId);
}
