package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    Optional<LoanApplication> findTopByMember_MemberIdOrderByAppliedAtDesc(Long memberId);

    List<LoanApplication> findAllByMember_MemberIdOrderByAppliedAtDesc(Long memberId);
}
