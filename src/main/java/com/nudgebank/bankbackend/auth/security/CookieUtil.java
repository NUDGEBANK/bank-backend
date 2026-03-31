package com.nudgebank.bankbackend.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

public class CookieUtil {
  private CookieUtil() {}

  public static String getCookieValue(HttpServletRequest req, String name) {
    if (req.getCookies() == null) return null;
    for (var c : req.getCookies()) {
      if (name.equals(c.getName())) return c.getValue();
    }
    return null;
  }

  public static void addHttpOnlyCookie(HttpServletResponse res, String name, String value, long maxAgeSeconds) {
    ResponseCookie cookie = ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(false)
        .path("/")
        .maxAge(maxAgeSeconds)
        .sameSite("Lax")
        .build();
    res.addHeader("Set-Cookie", cookie.toString());
  }

  public static void deleteCookie(HttpServletResponse res, String name) {
    ResponseCookie cookie = ResponseCookie.from(name, "")
        .httpOnly(true)
        .secure(false)
        .path("/")
        .maxAge(0)
        .sameSite("Lax")
        .build();
    res.addHeader("Set-Cookie", cookie.toString());
  }
}
