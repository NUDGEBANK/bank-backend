package com.nudgebank.bankbackend.finance.controller;

import com.nudgebank.bankbackend.finance.dto.FinancialStatusResponse;
import com.nudgebank.bankbackend.finance.service.FinancialStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/finance-status")
public class FinancialStatusController {

    private final FinancialStatusService financialStatusService;

    @GetMapping("/{memberId}/transaction/{transactionId}")
    public FinancialStatusResponse getFinancialStatus(
            @PathVariable Long memberId,
            @PathVariable Long transactionId
    ) {
        return financialStatusService.getFinancialStatus(memberId, transactionId);
    }
}