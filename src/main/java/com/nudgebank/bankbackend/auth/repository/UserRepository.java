package com.nudgebank.bankbackend.auth.repository;

import com.nudgebank.bankbackend.auth.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByUserId(String userId);
  boolean existsByUserId(String userId);
}
