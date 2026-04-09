package com.nudgebank.bankbackend.deposit.repository;

import com.nudgebank.bankbackend.deposit.domain.DepositProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DepositProductRepository extends JpaRepository<DepositProduct, Long> {
    Optional<DepositProduct> findByDepositProductType(String depositProductType);
}
