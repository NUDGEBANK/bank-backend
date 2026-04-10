package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.Loan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findTopByMember_MemberIdOrderByStartDateDescIdDesc(Long memberId);

    Optional<Loan> findTopByLoanApplication_IdOrderByIdDesc(Long loanApplicationId);

    Optional<Loan> findTopByMember_MemberIdAndLoanApplication_LoanProduct_LoanProductTypeOrderByStartDateDescIdDesc(
        Long memberId,
        String loanProductType
    );

    @Query("""
        select distinct l.member.memberId
        from Loan l
        join l.loanApplication la
        join la.loanProduct lp
        where lp.loanProductType = :loanProductType
          and l.status <> 'COMPLETED'
    """)
    List<Long> findDistinctMemberIdsByLoanProductTypeAndActive(@Param("loanProductType") String loanProductType);
}
