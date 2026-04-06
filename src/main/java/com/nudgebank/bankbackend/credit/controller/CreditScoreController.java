package com.nudgebank.bankbackend.credit.controller;

import com.nudgebank.bankbackend.credit.dto.CreditScoreResponse;
import com.nudgebank.bankbackend.credit.service.CreditScoreService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credits")
public class CreditScoreController {
  private final CreditScoreService creditScoreService;

  public CreditScoreController(CreditScoreService creditScoreService) {
    this.creditScoreService = creditScoreService;
  }

  @GetMapping("/me")
  public ResponseEntity<CreditScoreResponse> getMyScore(Authentication authentication) {
    Long userId = authentication != null && authentication.getPrincipal() instanceof Long principal
        ? principal
        : null;

    try {
      return ResponseEntity.ok(creditScoreService.getLatest(userId));
    } catch (IllegalArgumentException ex) {
      HttpStatus status = "UNAUTHORIZED".equals(ex.getMessage())
          ? HttpStatus.UNAUTHORIZED
          : HttpStatus.BAD_REQUEST;
      return ResponseEntity.status(status)
          .body(new CreditScoreResponse(false, ex.getMessage(), null, null, null, null, null, null, List.of(), List.of()));
    }
  }

  @PostMapping("/evaluate")
  public ResponseEntity<CreditScoreResponse> evaluate(Authentication authentication) {
    Long userId = authentication != null && authentication.getPrincipal() instanceof Long principal
        ? principal
        : null;

    try {
      return ResponseEntity.ok(creditScoreService.evaluate(userId));
    } catch (IllegalArgumentException ex) {
      HttpStatus status = "UNAUTHORIZED".equals(ex.getMessage())
          ? HttpStatus.UNAUTHORIZED
          : HttpStatus.BAD_REQUEST;
      return ResponseEntity.status(status)
          .body(new CreditScoreResponse(false, ex.getMessage(), null, null, null, null, null, null, List.of(), List.of()));
    }
  }
}
