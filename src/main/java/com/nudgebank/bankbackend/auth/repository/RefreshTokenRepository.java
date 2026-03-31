package com.nudgebank.bankbackend.auth.repository;

import com.nudgebank.bankbackend.auth.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
  Optional<RefreshToken> findByRid(String rid);
  void deleteByUserId(Long userId);
}
