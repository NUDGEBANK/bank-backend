package com.nudgebank.bankbackend.auth.service;

import com.nudgebank.bankbackend.auth.dto.LoginRequest;
import com.nudgebank.bankbackend.auth.dto.SignupRequest;
import com.nudgebank.bankbackend.auth.entity.RefreshToken;
import com.nudgebank.bankbackend.auth.entity.User;
import com.nudgebank.bankbackend.auth.repository.RefreshTokenRepository;
import com.nudgebank.bankbackend.auth.repository.UserRepository;
import com.nudgebank.bankbackend.auth.security.JwtProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  public record TokenPair(String accessToken, String refreshToken, String rid, long accessTtlSeconds, long refreshTtlSeconds) {}

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtProvider jwtProvider
  ) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtProvider = jwtProvider;
  }

  @Transactional
  public User signup(SignupRequest request) {
    if (request == null || isBlank(request.userId()) || isBlank(request.password()) || isBlank(request.name())) {
      throw new IllegalArgumentException("MISSING_FIELDS");
    }
    if (userRepository.existsByUserId(request.userId())) {
      throw new IllegalArgumentException("DUPLICATE_USER_ID");
    }

    User user = new User();
    user.setUserId(request.userId());
    user.setName(request.name());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setBirth(request.birth());
    user.setGender(request.gender());

    return userRepository.save(user);
  }

  public User login(LoginRequest request) {
    if (request == null || isBlank(request.userId()) || isBlank(request.password())) {
      throw new IllegalArgumentException("MISSING_FIELDS");
    }

    Optional<User> user = userRepository.findByUserId(request.userId());
    if (user.isEmpty()) {
      throw new IllegalArgumentException("INVALID_CREDENTIALS");
    }
    if (!passwordEncoder.matches(request.password(), user.get().getPasswordHash())) {
      throw new IllegalArgumentException("INVALID_CREDENTIALS");
    }
    return user.get();
  }

  @Transactional
  public TokenPair issueTokens(User user) {
    refreshTokenRepository.deleteByUserId(user.getId());

    String rid = UUID.randomUUID().toString().replace("-", "");
    String accessToken = jwtProvider.createAccessToken(user.getId());
    String refreshToken = jwtProvider.createRefreshToken(user.getId(), rid);

    RefreshToken stored = new RefreshToken();
    stored.setRid(rid);
    stored.setUserId(user.getId());
    stored.setToken(refreshToken);
    stored.setExpiresAt(Instant.now().plusSeconds(jwtProvider.getRefreshTtlSeconds()));
    refreshTokenRepository.save(stored);

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
      refreshTokenRepository.deleteByUserId(userId);
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

}
