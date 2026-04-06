package com.nudgebank.bankbackend.finance.repository;

import com.nudgebank.bankbackend.finance.domain.AgeGroupBaseline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgeGroupBaselineRepository extends JpaRepository<AgeGroupBaseline, String> {
    Optional<AgeGroupBaseline> findByAgeGroup(String ageGroup);
}
