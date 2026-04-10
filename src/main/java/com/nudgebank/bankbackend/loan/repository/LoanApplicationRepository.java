package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.LoanApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    Optional<LoanApplication> findTopByMember_MemberIdOrderByAppliedAtDesc(Long memberId);

    Optional<LoanApplication> findTopByMember_MemberIdAndLoanProduct_LoanProductTypeOrderByAppliedAtDesc(
        Long memberId,
        String loanProductType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoanApplication> findByIdAndMember_MemberId(Long loanApplicationId, Long memberId);

    // 승인/거절이 동시에 들어와도 락 처리
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select la from LoanApplication la where la.id = :loanApplicationId")
    Optional<LoanApplication> findByIdForUpdate(@Param("loanApplicationId") Long loanApplicationId);

    List<LoanApplication> findAllByMember_MemberIdOrderByAppliedAtDesc(Long memberId);
}
