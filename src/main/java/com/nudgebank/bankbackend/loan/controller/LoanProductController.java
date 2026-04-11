package com.nudgebank.bankbackend.loan.controller;

import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import com.nudgebank.bankbackend.loan.dto.LoanEligibilityRequest;
import com.nudgebank.bankbackend.loan.dto.LoanEligibilityResponse;
import com.nudgebank.bankbackend.loan.dto.LoanProductDetailResponse;
import com.nudgebank.bankbackend.loan.dto.LoanProductListResponse;
import com.nudgebank.bankbackend.loan.service.LoanEligibilityService;
import com.nudgebank.bankbackend.loan.service.LoanProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/loan-products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductService loanProductService;
    private final LoanEligibilityService loanEligibilityService;

    // 대출 상품 목록 조회
    @GetMapping
    public List<LoanProductListResponse> getLoanProducts() {
        return loanProductService.getLoanProducts();
    }

    // 대출 상품 상세 조회
    @GetMapping("/{loanProductId}")
    public LoanProductDetailResponse getLoanProductDetail(@PathVariable Long loanProductId) {
        return loanProductService.getLoanProductDetail(loanProductId);
    }

    // 신용점수 기반 대출 가능 여부 조회
    @PostMapping("/eligibility")
    public LoanEligibilityResponse checkEligibility(
            @RequestBody LoanEligibilityRequest request,
            Authentication authentication
    ) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return loanEligibilityService.check(memberId, request);
    }
}
