package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanHistoryRepository extends JpaRepository<LoanHistory, Long> {

    List<LoanHistory> findAllByCard_CardIdAndStatusOrderByExpectedRepaymentDateAscCreatedAtDesc(Long cardId, String status);

    List<LoanHistory> findAllByCard_CardIdAndStatusInOrderByExpectedRepaymentDateAscCreatedAtDesc(
        Long cardId,
        List<String> statuses
    );

    Optional<LoanHistory> findTopByCard_CardIdAndStatusOrderByCreatedAtDesc(Long cardId, String status);

    Optional<LoanHistory> findTopByMember_MemberIdOrderByCreatedAtDesc(Long memberId);

    Optional<LoanHistory> findTopByMember_MemberIdAndCard_CardIdAndTotalPrincipalAndStartDateOrderByCreatedAtDesc(
        Long memberId,
        Long cardId,
        java.math.BigDecimal totalPrincipal,
        java.time.LocalDate startDate
    );

    Optional<LoanHistory> findTopByMember_MemberIdAndCard_CardIdAndTotalPrincipalAndStartDateAndEndDateOrderByCreatedAtDesc(
        Long memberId,
        Long cardId,
        java.math.BigDecimal totalPrincipal,
        java.time.LocalDate startDate,
        java.time.LocalDate endDate
    );

    boolean existsByRepaymentAccountNumber(String repaymentAccountNumber);
}
