package com.nudgebank.bankbackend.card.controller;

import com.nudgebank.bankbackend.card.dto.CardHistoryResponse;
import com.nudgebank.bankbackend.card.dto.CardIssueRequest;
import com.nudgebank.bankbackend.card.dto.CardIssueResponse;
import com.nudgebank.bankbackend.card.service.CardHistoryService;
import com.nudgebank.bankbackend.card.service.CardIssueService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
public class CardController {
  private final CardIssueService cardIssueService;
  private final CardHistoryService cardHistoryService;

  public CardController(CardIssueService cardIssueService, CardHistoryService cardHistoryService) {
    this.cardIssueService = cardIssueService;
    this.cardHistoryService = cardHistoryService;
  }

  @PostMapping("/apply")
  public ResponseEntity<CardIssueResponse> apply(
      @RequestBody CardIssueRequest request,
      Authentication authentication
  ) {
    Long userId = extractUserId(authentication);

    try {
      return ResponseEntity.ok(cardIssueService.issue(userId, request));
    } catch (IllegalArgumentException ex) {
      HttpStatus status = "UNAUTHORIZED".equals(ex.getMessage())
          ? HttpStatus.UNAUTHORIZED
          : HttpStatus.BAD_REQUEST;
      return ResponseEntity.status(status)
          .body(new CardIssueResponse(false, ex.getMessage(), null, null, null, null, null, null, null, null, null));
    } catch (EntityNotFoundException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new CardIssueResponse(false, ex.getMessage(), null, null, null, null, null, null, null, null, null));
    } catch (IllegalStateException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(new CardIssueResponse(false, ex.getMessage(), null, null, null, null, null, null, null, null, null));
    }
  }

  @GetMapping("/history")
  public ResponseEntity<CardHistoryResponse> history(Authentication authentication) {
    Long userId = extractUserId(authentication);

    try {
      return ResponseEntity.ok(cardHistoryService.getHistory(userId));
    } catch (IllegalArgumentException ex) {
      HttpStatus status = "UNAUTHORIZED".equals(ex.getMessage())
          ? HttpStatus.UNAUTHORIZED
          : HttpStatus.BAD_REQUEST;
      return ResponseEntity.status(status)
          .body(new CardHistoryResponse(false, ex.getMessage(), List.of()));
    } catch (EntityNotFoundException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
              .body(new CardHistoryResponse(false, ex.getMessage(), List.of()));
    }
  }

  private Long extractUserId(Authentication authentication) {
    if (authentication == null) {
      return null;
}

    Object principal = authentication.getPrincipal();
    return principal instanceof Long ? (Long) principal : null;
  }
}