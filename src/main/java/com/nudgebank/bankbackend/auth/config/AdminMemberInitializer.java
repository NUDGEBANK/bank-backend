package com.nudgebank.bankbackend.auth.config;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminMemberInitializer implements CommandLineRunner {

  private static final Long ADMIN_MEMBER_ID = 100L;
  private static final String ADMIN_LOGIN_ID = "admin";
  private static final String ADMIN_NAME = "관리자";
  private static final String ADMIN_PASSWORD = "admin";
  private static final LocalDate ADMIN_BIRTH = LocalDate.of(1990, 1, 1);
  private static final String ADMIN_GENDER = "남성";
  private static final String ADMIN_PHONE_NUMBER = "999-9999-9999";

  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public void run(String... args) {
    Integer existingByMemberId = jdbcTemplate.queryForObject(
        "select count(*) from member where member_id = ?",
        Integer.class,
        ADMIN_MEMBER_ID
    );

    if (existingByMemberId != null && existingByMemberId > 0) {
      syncMemberSequence();
      return;
    }

    Integer existingByLoginId = jdbcTemplate.queryForObject(
        "select count(*) from member where id = ?",
        Integer.class,
        ADMIN_LOGIN_ID
    );

    if (existingByLoginId != null && existingByLoginId > 0) {
      return;
    }

    jdbcTemplate.update(
        """
        insert into member (
          member_id,
          id,
          name,
          password,
          birth,
          created_at,
          gender,
          phone_number
        ) values (?, ?, ?, ?, ?, now(), ?, ?)
        """,
        ADMIN_MEMBER_ID,
        ADMIN_LOGIN_ID,
        ADMIN_NAME,
        passwordEncoder.encode(ADMIN_PASSWORD),
        ADMIN_BIRTH,
        ADMIN_GENDER,
        ADMIN_PHONE_NUMBER
    );

    syncMemberSequence();
  }

  private void syncMemberSequence() {
    jdbcTemplate.execute(
        """
        select setval(
          pg_get_serial_sequence('member', 'member_id'),
          (select greatest(coalesce(max(member_id), 1), 100) from member)
        )
        """
    );
  }
}
