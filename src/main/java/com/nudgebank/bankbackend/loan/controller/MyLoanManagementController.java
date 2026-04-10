package com.nudgebank.bankbackend.loan.controller;

import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import com.nudgebank.bankbackend.loan.dto.MyLoanRepaymentHistoryResponse;
import com.nudgebank.bankbackend.loan.dto.MyLoanRepaymentScheduleResponse;
import com.nudgebank.bankbackend.loan.dto.MyLoanSummaryResponse;
import com.nudgebank.bankbackend.loan.service.MyLoanManagementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/loans/me")
public class MyLoanManagementController {

    private final MyLoanManagementService myLoanManagementService;

    @GetMapping("/summary")
    public MyLoanSummaryResponse getSummary(
        Authentication authentication,
        @RequestParam(required = false) String productKey
    ) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return myLoanManagementService.getSummary(memberId, productKey);
    }

    @GetMapping("/repayment-schedules")
    public List<MyLoanRepaymentScheduleResponse> getRepaymentSchedules(
        Authentication authentication,
        @RequestParam(required = false) String productKey
    ) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return myLoanManagementService.getRepaymentSchedules(memberId, productKey);
    }

    @GetMapping("/repayment-histories")
    public List<MyLoanRepaymentHistoryResponse> getRepaymentHistories(
        Authentication authentication,
        @RequestParam(required = false) String productKey
    ) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return myLoanManagementService.getRepaymentHistories(memberId, productKey);
    }
}
