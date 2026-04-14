package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.RepaymentSchedule;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepaymentScheduleRepository extends JpaRepository<RepaymentSchedule, Long> {

    List<RepaymentSchedule> findAllByLoanHistory_Member_MemberIdOrderByDueDateAsc(Long memberId);

    List<RepaymentSchedule> findAllByLoanHistory_IdOrderByDueDateAsc(Long loanHistoryId);

    List<RepaymentSchedule> findAllByLoanHistory_IdAndIsSettledFalseOrderByDueDateAsc(Long loanHistoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select rs
        from RepaymentSchedule rs
        where rs.loanHistory.id = :loanHistoryId
          and rs.isSettled = false
        order by rs.dueDate asc, rs.scheduleId asc
    """)
    List<RepaymentSchedule> findAllUnsettledByLoanHistoryIdForUpdate(@Param("loanHistoryId") Long loanHistoryId);
}
