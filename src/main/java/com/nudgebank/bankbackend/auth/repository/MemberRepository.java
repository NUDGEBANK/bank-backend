package com.nudgebank.bankbackend.auth.repository;

import com.nudgebank.bankbackend.auth.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {
  @Query("select m from Member m where m.id = :loginId")
  Optional<Member> findByLoginId(@Param("loginId") String loginId);

  @Query("select count(m) > 0 from Member m where m.id = :loginId")
  boolean existsByLoginId(@Param("loginId") String loginId);
}
