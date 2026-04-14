package com.nudgebank.bankbackend.deposit.controller;

import com.nudgebank.bankbackend.deposit.dto.DepositProductResponse;
import com.nudgebank.bankbackend.deposit.service.DepositProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deposit-products")
@RequiredArgsConstructor
public class DepositProductController {

    private final DepositProductService depositProductService;

    @GetMapping
    public List<DepositProductResponse> getDepositProducts() {
        return depositProductService.getDepositProducts();
    }

    @GetMapping("/{depositProductId}")
    public DepositProductResponse getDepositProduct(@PathVariable Long depositProductId) {
        return depositProductService.getDepositProduct(depositProductId);
    }
}
