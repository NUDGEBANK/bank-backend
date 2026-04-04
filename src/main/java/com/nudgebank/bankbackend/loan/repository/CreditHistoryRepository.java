package com.nudgebank.bankbackend.loan.repository;

import com.nudgebank.bankbackend.loan.domain.CreditHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditHistoryRepository extends JpaRepository<CreditHistory, Long> {
}