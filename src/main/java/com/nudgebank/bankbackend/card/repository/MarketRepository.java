package com.nudgebank.bankbackend.card.repository;

import com.nudgebank.bankbackend.card.domain.Market;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketRepository extends JpaRepository<Market, Long> {
}