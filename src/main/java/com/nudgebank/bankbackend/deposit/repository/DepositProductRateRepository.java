package com.nudgebank.bankbackend.deposit.repository;

import com.nudgebank.bankbackend.deposit.domain.DepositProductRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepositProductRateRepository extends JpaRepository<DepositProductRate, Long> {
    List<DepositProductRate> findAllByDepositProduct_DepositProductIdOrderByMinSavingMonthAsc(Long depositProductId);

    Optional<DepositProductRate> findTopByDepositProduct_DepositProductIdAndMinSavingMonthLessThanEqualAndMaxSavingMonthGreaterThanEqual(
        Long depositProductId,
        Integer minSavingMonth,
        Integer maxSavingMonth
    );

    Optional<DepositProductRate> findByDepositProduct_DepositProductIdAndMinSavingMonthAndMaxSavingMonth(
        Long depositProductId,
        Integer minSavingMonth,
        Integer maxSavingMonth
    );
}
