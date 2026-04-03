package com.nudgebank.bankbackend.card.controller;

import com.nudgebank.bankbackend.card.dto.CardPaymentRequest;
import com.nudgebank.bankbackend.card.service.CardPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cards")
public class CardPaymentController {

    private final CardPaymentService cardPaymentService;

    @PostMapping("/payment")
    public ResponseEntity<Map<String, Long>> pay(@RequestBody CardPaymentRequest request) {
        Long transactionId = cardPaymentService.processPayment(request);
        return ResponseEntity.ok(Map.of("transactionId", transactionId));
    }
}