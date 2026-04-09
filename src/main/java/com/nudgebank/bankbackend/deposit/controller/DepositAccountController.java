package com.nudgebank.bankbackend.deposit.controller;

import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import com.nudgebank.bankbackend.deposit.dto.DepositAccountActionResponse;
import com.nudgebank.bankbackend.deposit.dto.DepositAccountCreateRequest;
import com.nudgebank.bankbackend.deposit.dto.DepositAccountDetailResponse;
import com.nudgebank.bankbackend.deposit.dto.DepositAccountSummaryResponse;
import com.nudgebank.bankbackend.deposit.dto.DepositPaymentRequest;
import com.nudgebank.bankbackend.deposit.service.DepositAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deposit-accounts")
@RequiredArgsConstructor
public class DepositAccountController {

    private final DepositAccountService depositAccountService;

    @PostMapping
    public DepositAccountActionResponse createDepositAccount(
        Authentication authentication,
        @RequestBody DepositAccountCreateRequest request
    ) {
        return depositAccountService.create(extractMemberId(authentication), request);
    }

    @GetMapping("/me")
    public List<DepositAccountSummaryResponse> getMyDepositAccounts(Authentication authentication) {
        return depositAccountService.getMyDepositAccounts(extractMemberId(authentication));
    }

    @GetMapping("/me/{depositAccountId}")
    public DepositAccountDetailResponse getMyDepositAccount(
        Authentication authentication,
        @PathVariable Long depositAccountId
    ) {
        return depositAccountService.getMyDepositAccount(extractMemberId(authentication), depositAccountId);
    }

    @PostMapping("/{depositAccountId}/deposit")
    public DepositAccountActionResponse deposit(
        Authentication authentication,
        @PathVariable Long depositAccountId,
        @RequestBody DepositPaymentRequest request
    ) {
        return depositAccountService.deposit(extractMemberId(authentication), depositAccountId, request);
    }

    @PostMapping("/{depositAccountId}/withdraw")
    public DepositAccountActionResponse withdraw(
        Authentication authentication,
        @PathVariable Long depositAccountId
    ) {
        return depositAccountService.withdraw(extractMemberId(authentication), depositAccountId);
    }

    private Long extractMemberId(Authentication authentication) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new IllegalArgumentException("인증 정보가 없습니다.");
        }
        return memberId;
    }
}
