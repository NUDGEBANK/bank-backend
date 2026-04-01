package com.nudgebank.bankbackend.card.controller;

import com.nudgebank.bankbackend.card.dto.CardIssueRequest;
import com.nudgebank.bankbackend.card.dto.CardIssueResponse;
import com.nudgebank.bankbackend.card.service.CardIssueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public class CardController {
  private final CardIssueService cardIssueService;

  public CardController(CardIssueService cardIssueService) {
    this.cardIssueService = cardIssueService;
  }

  @PostMapping("/apply")
  public ResponseEntity<CardIssueResponse> apply(
      @RequestBody CardIssueRequest request,
      Authentication authentication
  ) {
    Long userId = authentication != null && authentication.getPrincipal() instanceof Long principal
        ? principal
        : null;

    try {
      return ResponseEntity.ok(cardIssueService.issue(userId, request));
    } catch (IllegalArgumentException ex) {
      HttpStatus status = "UNAUTHORIZED".equals(ex.getMessage())
          ? HttpStatus.UNAUTHORIZED
          : HttpStatus.BAD_REQUEST;
      return ResponseEntity.status(status)
          .body(new CardIssueResponse(false, ex.getMessage(), null, null, null, null, null, null, null, null, null));
    } catch (IllegalStateException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(new CardIssueResponse(false, ex.getMessage(), null, null, null, null, null, null, null, null, null));
    }
  }
}
