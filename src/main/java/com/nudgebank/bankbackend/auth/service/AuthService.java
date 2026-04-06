package com.nudgebank.bankbackend.auth.service;

import com.nudgebank.bankbackend.auth.dto.LoginRequest;
import com.nudgebank.bankbackend.auth.dto.SignupRequest;
import com.nudgebank.bankbackend.auth.domain.RefreshToken;
import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.repository.RefreshTokenRepository;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.auth.security.JwtProvider;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  public record TokenPair(String accessToken, String refreshToken, String rid, long accessTtlSeconds, long refreshTtlSeconds) {}
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final MemberRepository memberRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  public AuthService(
      MemberRepository memberRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtProvider jwtProvider
  ) {
    this.memberRepository = memberRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtProvider = jwtProvider;
  }

  @Transactional
  public Member signup(SignupRequest request) {
    if (request == null
        || isBlank(request.userId())
        || isBlank(request.password())
        || isBlank(request.name())
        || isBlank(request.phoneNumber())) {
      throw new IllegalArgumentException("MISSING_FIELDS");
    }
    if (memberRepository.existsByLoginId(request.userId())) {
      throw new IllegalArgumentException("DUPLICATE_USER_ID");
    }

    return memberRepository.save(
        Member.create(
            request.userId(),
            request.name(),
            passwordEncoder.encode(request.password()),
            request.birth(),
            OffsetDateTime.now(),
            request.gender(),
            request.phoneNumber()
        )
    );
  }

  public Member login(LoginRequest request) {
    if (request == null || isBlank(request.userId()) || isBlank(request.password())) {
      throw new IllegalArgumentException("MISSING_FIELDS");
    }

    Optional<Member> member = memberRepository.findByLoginId(request.userId());
    if (member.isEmpty()) {
      throw new IllegalArgumentException("INVALID_CREDENTIALS");
    }
    if (!passwordEncoder.matches(request.password(), member.get().getPassword())) {
      throw new IllegalArgumentException("INVALID_CREDENTIALS");
    }
    return member.get();
  }

  public TokenPair issueTokens(Long memberId) {
    String rid = UUID.randomUUID().toString().replace("-", "");
    String accessToken = jwtProvider.createAccessToken(memberId);
    String refreshToken = jwtProvider.createRefreshToken(memberId, rid);

    try {
      refreshTokenRepository.deleteByMemberId(memberId);
      refreshTokenRepository.save(
          RefreshToken.create(
              rid,
              memberId,
              refreshToken,
              Instant.now().plusSeconds(jwtProvider.getRefreshTtlSeconds())
          )
      );
    } catch (RuntimeException ex) {
      log.warn("Refresh token persistence failed for memberId={}", memberId, ex);
    }

    return new TokenPair(
        accessToken,
        refreshToken,
        rid,
        jwtProvider.getAccessTtlSeconds(),
        jwtProvider.getRefreshTtlSeconds()
    );
  }

  public Optional<RefreshToken> findRefreshToken(String rid) {
    return refreshTokenRepository.findByRid(rid);
  }

  @Transactional
  public void deleteRefreshTokenByRid(String rid) {
    if (rid != null && !rid.isBlank()) {
      refreshTokenRepository.deleteById(rid);
    }
  }

  @Transactional
  public void deleteRefreshTokenByUserId(Long userId) {
    if (userId != null) {
      refreshTokenRepository.deleteByMemberId(userId);
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
