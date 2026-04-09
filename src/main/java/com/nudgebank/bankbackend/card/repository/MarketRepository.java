package com.nudgebank.bankbackend.card.repository;

import com.nudgebank.bankbackend.card.domain.Market;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketRepository extends JpaRepository<Market, Long> {
    Optional<Market> findByMarketNameAndCategory_CategoryId(String marketName, Long categoryId);
}
