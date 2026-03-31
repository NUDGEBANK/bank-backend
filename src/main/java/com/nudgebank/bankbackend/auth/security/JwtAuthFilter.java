package com.nudgebank.bankbackend.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthFilter extends OncePerRequestFilter {
  private final JwtProvider jwtProvider;

  public JwtAuthFilter(JwtProvider jwtProvider) {
    this.jwtProvider = jwtProvider;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String token = TokenResolver.resolveAccessToken(req);

    if (token == null || token.isBlank()) {
      chain.doFilter(req, res);
      return;
    }

    if (!jwtProvider.isValid(token)) {
      SecurityContextHolder.clearContext();
      chain.doFilter(req, res);
      return;
    }

    Long userId = Long.valueOf(jwtProvider.parseClaims(token).getSubject());
    var auth = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
    SecurityContextHolder.getContext().setAuthentication(auth);
    chain.doFilter(req, res);
  }
}
