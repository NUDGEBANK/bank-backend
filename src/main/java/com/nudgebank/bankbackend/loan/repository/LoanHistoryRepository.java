package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanHistoryRepository extends JpaRepository<LoanHistory, Long> {

    Optional<LoanHistory> findByCard_CardIdAndStatus(Long cardId, String status);

    Optional<LoanHistory> findTopByMember_MemberIdOrderByCreatedAtDesc(Long memberId);

    Optional<LoanHistory> findTopByMember_MemberIdAndCard_CardIdAndTotalPrincipalAndStartDateOrderByCreatedAtDesc(
        Long memberId,
        Long cardId,
        java.math.BigDecimal totalPrincipal,
        java.time.LocalDate startDate
    );
}
