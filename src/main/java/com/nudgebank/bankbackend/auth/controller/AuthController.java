package com.nudgebank.bankbackend.auth.controller;

import com.nudgebank.bankbackend.auth.dto.AuthResponse;
import com.nudgebank.bankbackend.auth.dto.LoginRequest;
import com.nudgebank.bankbackend.auth.dto.SignupRequest;
import com.nudgebank.bankbackend.auth.domain.RefreshToken;
import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.security.CookieUtil;
import com.nudgebank.bankbackend.auth.security.JwtProvider;
import com.nudgebank.bankbackend.auth.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private static final String AT_COOKIE = "AT";
  private static final String RT_COOKIE = "RT";

  private final AuthService authService;
  private final JwtProvider jwtProvider;

  public AuthController(AuthService authService, JwtProvider jwtProvider) {
    this.authService = authService;
    this.jwtProvider = jwtProvider;
  }

  @PostMapping("/signup")
  public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request, HttpServletResponse res) {
    try {
      Member member = authService.signup(request);
      AuthService.TokenPair tokens = authService.issueTokens(member);
      setAuthCookies(res, tokens);
      return ResponseEntity.ok(new AuthResponse(true, "OK"));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new AuthResponse(false, ex.getMessage()));
    }
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request, HttpServletResponse res) {
    try {
      Member member = authService.login(request);
      AuthService.TokenPair tokens = authService.issueTokens(member);
      setAuthCookies(res, tokens);
      return ResponseEntity.ok(new AuthResponse(true, "OK"));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, ex.getMessage()));
    }
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(HttpServletRequest req, HttpServletResponse res) {
    String rt = CookieUtil.getCookieValue(req, RT_COOKIE);
    if (rt == null || rt.isBlank()) {
      clearAuthCookies(res);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "NO_REFRESH"));
    }

    if (!jwtProvider.isValid(rt)) {
      clearAuthCookies(res);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "INVALID_REFRESH"));
    }

    Claims claims = jwtProvider.parseClaims(rt);
    String rid = claims.getId();
    if (rid == null || rid.isBlank()) {
      clearAuthCookies(res);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "RID_MISSING"));
    }

    RefreshToken stored = authService.findRefreshToken(rid).orElse(null);
    if (stored == null || !stored.getToken().equals(rt)) {
      clearAuthCookies(res);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "REFRESH_MISMATCH"));
    }
    if (stored.getExpiresAt().isBefore(Instant.now())) {
      authService.deleteRefreshTokenByRid(rid);
      clearAuthCookies(res);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse(false, "REFRESH_EXPIRED"));
    }

    Long userId = Long.parseLong(claims.getSubject());
    Member member = new Member();
    member.setId(userId);

    authService.deleteRefreshTokenByRid(rid);
    AuthService.TokenPair tokens = authService.issueTokens(member);
    setAuthCookies(res, tokens);
    return ResponseEntity.ok(new AuthResponse(true, "OK"));
  }

  @PostMapping("/logout")
  public ResponseEntity<AuthResponse> logout(HttpServletRequest req, HttpServletResponse res) {
    String rt = CookieUtil.getCookieValue(req, RT_COOKIE);
    if (rt != null && !rt.isBlank() && jwtProvider.isValid(rt)) {
      String rid = jwtProvider.parseClaims(rt).getId();
      authService.deleteRefreshTokenByRid(rid);
    }
    clearAuthCookies(res);
    return ResponseEntity.ok(new AuthResponse(true, "OK"));
  }

  private void setAuthCookies(HttpServletResponse res, AuthService.TokenPair tokens) {
    CookieUtil.addHttpOnlyCookie(res, AT_COOKIE, tokens.accessToken(), tokens.accessTtlSeconds());
    CookieUtil.addHttpOnlyCookie(res, RT_COOKIE, tokens.refreshToken(), tokens.refreshTtlSeconds());
  }

  private void clearAuthCookies(HttpServletResponse res) {
    CookieUtil.deleteCookie(res, AT_COOKIE);
    CookieUtil.deleteCookie(res, RT_COOKIE);
  }
}
