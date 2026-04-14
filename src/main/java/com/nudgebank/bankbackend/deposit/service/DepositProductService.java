package com.nudgebank.bankbackend.deposit.service;

import com.nudgebank.bankbackend.deposit.domain.DepositProduct;
import com.nudgebank.bankbackend.deposit.domain.DepositProductRate;
import com.nudgebank.bankbackend.deposit.dto.DepositProductRateResponse;
import com.nudgebank.bankbackend.deposit.dto.DepositProductResponse;
import com.nudgebank.bankbackend.deposit.repository.DepositProductRateRepository;
import com.nudgebank.bankbackend.deposit.repository.DepositProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepositProductService {

    private final DepositProductRepository depositProductRepository;
    private final DepositProductRateRepository depositProductRateRepository;

    public List<DepositProductResponse> getDepositProducts() {
        return depositProductRepository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public DepositProductResponse getDepositProduct(Long depositProductId) {
        DepositProduct product = depositProductRepository.findById(depositProductId)
            .orElseThrow(() -> new EntityNotFoundException("예적금 상품을 찾을 수 없습니다."));
        return toResponse(product);
    }

    private DepositProductResponse toResponse(DepositProduct product) {
        List<DepositProductRateResponse> rates = depositProductRateRepository
            .findAllByDepositProduct_DepositProductIdOrderByMinSavingMonthAsc(product.getDepositProductId())
            .stream()
            .map(this::toRateResponse)
            .toList();

        return new DepositProductResponse(
            product.getDepositProductId(),
            product.getDepositProductName(),
            product.getDepositProductType(),
            product.getDepositProductDescription(),
            product.getDepositMinAmount(),
            product.getDepositMaxAmount(),
            product.getMinSavingMonth(),
            product.getMaxSavingMonth(),
            rates
        );
    }

    private DepositProductRateResponse toRateResponse(DepositProductRate rate) {
        return new DepositProductRateResponse(
            rate.getDepositProductRateId(),
            rate.getMinSavingMonth(),
            rate.getMaxSavingMonth(),
            rate.getInterestRate()
        );
    }
}
