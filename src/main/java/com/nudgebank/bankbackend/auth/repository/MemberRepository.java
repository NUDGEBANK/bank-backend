package com.nudgebank.bankbackend.auth.repository;

import com.nudgebank.bankbackend.auth.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
  Optional<Member> findByUserId(String userId);
  boolean existsByUserId(String userId);
}
