package com.nudgebank.bankbackend.loan.controller;

import com.nudgebank.bankbackend.loan.dto.LoanProductDetailResponse;
import com.nudgebank.bankbackend.loan.dto.LoanProductListResponse;
import com.nudgebank.bankbackend.loan.service.LoanProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loan-products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductService loanProductService;

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
}