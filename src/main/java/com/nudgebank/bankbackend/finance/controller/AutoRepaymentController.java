package com.nudgebank.bankbackend.finance.controller;

import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import com.nudgebank.bankbackend.finance.dto.AutoRepaymentDecisionResponse;
import com.nudgebank.bankbackend.finance.dto.AutoRepaymentRequest;
import com.nudgebank.bankbackend.finance.service.AutoRepaymentExecutionService;
import com.nudgebank.bankbackend.finance.service.AutoRepaymentPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auto-repayment")
public class AutoRepaymentController {

    private final AutoRepaymentPolicyService autoRepaymentPolicyService;
    private final AutoRepaymentExecutionService autoRepaymentExecutionService;

    @GetMapping("/me/{transactionId}")
    public AutoRepaymentDecisionResponse getAutoRepaymentDecision(
            Authentication authentication,
            @PathVariable Long transactionId
    ) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return autoRepaymentPolicyService.decideAutoRepayment(memberId, transactionId);
    }

    @PostMapping("/execute")
    public ResponseEntity<Void> receivePaymentSignal(@RequestBody AutoRepaymentRequest signal) {

        autoRepaymentExecutionService.executeAfterPaymentReal(signal.getMemberId(), signal.getCardTransaction());

        return ResponseEntity.accepted().build();
    }

}
