package com.nudgebank.bankbackend.auth.security;

import jakarta.servlet.http.HttpServletRequest;

public class TokenResolver {
  private TokenResolver() {}

  public static String resolveAccessToken(HttpServletRequest req) {
    String atCookie = CookieUtil.getCookieValue(req, "AT");
    if (atCookie != null && !atCookie.isBlank()) {
      return atCookie;
    }
    return null;
  }
}
