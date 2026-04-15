package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.Loan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findTopByMember_MemberIdOrderByStartDateDescIdDesc(Long memberId);

    Optional<Loan> findTopByLoanApplication_IdOrderByIdDesc(Long loanApplicationId);

    Optional<Loan> findTopByMember_MemberIdAndLoanApplication_LoanProduct_LoanProductTypeOrderByStartDateDescIdDesc(
        Long memberId,
        String loanProductType
    );

    Optional<Loan> findTopByMember_MemberIdAndLoanApplication_Card_CardIdAndLoanApplication_LoanProduct_LoanProductTypeOrderByStartDateDescIdDesc(
        Long memberId,
        Long cardId,
        String loanProductType
    );

    Optional<Loan> findTopByMember_MemberIdAndLoanApplication_Card_CardIdAndPrincipalAmountAndStartDateAndEndDateOrderByIdDesc(
        Long memberId,
        Long cardId,
        java.math.BigDecimal principalAmount,
        java.time.LocalDate startDate,
        java.time.LocalDate endDate
    );

    Optional<Loan> findTopByMember_MemberIdAndLoanApplication_Card_CardIdAndPrincipalAmountAndStartDateOrderByIdDesc(
        Long memberId,
        Long cardId,
        java.math.BigDecimal principalAmount,
        java.time.LocalDate startDate
    );

}
