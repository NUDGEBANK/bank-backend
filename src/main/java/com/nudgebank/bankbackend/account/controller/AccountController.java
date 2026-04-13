package com.nudgebank.bankbackend.account.controller;

import com.nudgebank.bankbackend.account.dto.AccountSummaryResponse;
import com.nudgebank.bankbackend.account.service.AccountService;
import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;

  @GetMapping("/me")
  public List<AccountSummaryResponse> getMyAccounts(Authentication authentication) {
    Long memberId = SecurityUtil.extractUserId(authentication);
    if (memberId == null) {
      throw new IllegalArgumentException("인증 정보가 없습니다.");
    }
    return accountService.getMyAccounts(memberId);
  }
}
