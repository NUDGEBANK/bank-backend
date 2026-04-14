package com.nudgebank.bankbackend.deposit.repository;

import com.nudgebank.bankbackend.deposit.domain.DepositAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepositAccountRepository extends JpaRepository<DepositAccount, Long> {
    List<DepositAccount> findAllByMemberIdOrderByStartDateDesc(Long memberId);

    Optional<DepositAccount> findByDepositAccountIdAndMemberId(Long depositAccountId, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select da
        from DepositAccount da
        join fetch da.depositProduct
        join fetch da.depositProductRate
        join fetch da.account
        where da.depositAccountId = :depositAccountId
          and da.memberId = :memberId
        """)
    Optional<DepositAccount> findByDepositAccountIdAndMemberIdForUpdate(
        @Param("depositAccountId") Long depositAccountId,
        @Param("memberId") Long memberId
    );
}
