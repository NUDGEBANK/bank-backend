package com.nudgebank.bankbackend.card.repository;

import com.nudgebank.bankbackend.card.domain.MarketCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketCategoryRepository extends JpaRepository<MarketCategory, Long> {
}