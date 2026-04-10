package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.LoanApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    Optional<LoanApplication> findTopByMember_MemberIdOrderByAppliedAtDesc(Long memberId);

    Optional<LoanApplication> findTopByMember_MemberIdAndLoanProduct_LoanProductTypeOrderByAppliedAtDesc(
        Long memberId,
        String loanProductType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoanApplication> findByIdAndMember_MemberId(Long loanApplicationId, Long memberId);

    List<LoanApplication> findAllByMember_MemberIdOrderByAppliedAtDesc(Long memberId);
}
