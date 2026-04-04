package com.nudgebank.bankbackend.finance.controller;

import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import com.nudgebank.bankbackend.finance.dto.FinancialStatusResponse;
import com.nudgebank.bankbackend.finance.service.FinancialStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/finance-status")
public class FinancialStatusController {

    private final FinancialStatusService financialStatusService;

    @GetMapping("/transaction/{transactionId}")
    public FinancialStatusResponse getFinancialStatus(
            @PathVariable Long transactionId,
            Authentication authentication
    ) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return financialStatusService.getFinancialStatus(memberId, transactionId);
    }
}