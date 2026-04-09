package com.nudgebank.bankbackend.card.controller;

import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import com.nudgebank.bankbackend.card.dto.CardPaymentRequest;
import com.nudgebank.bankbackend.card.dto.CardPaymentResponse;
import com.nudgebank.bankbackend.card.service.CardPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cards")
public class CardPaymentController {

    private final CardPaymentService cardPaymentService;

    @PostMapping("/payment")
    public ResponseEntity<CardPaymentResponse> pay(
            @RequestBody CardPaymentRequest request,
            Authentication authentication
    ) {
        Long userId = SecurityUtil.extractUserId(authentication);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        CardPaymentResponse response = cardPaymentService.processPayment(request);
        return ResponseEntity.ok(response);
    }
}
