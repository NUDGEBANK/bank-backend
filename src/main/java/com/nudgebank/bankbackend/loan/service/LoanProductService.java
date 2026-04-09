package com.nudgebank.bankbackend.loan.service;

import com.nudgebank.bankbackend.loan.domain.LoanProduct;
import com.nudgebank.bankbackend.loan.dto.LoanProductDetailResponse;
import com.nudgebank.bankbackend.loan.dto.LoanProductListResponse;
import com.nudgebank.bankbackend.loan.repository.LoanProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanProductService {

    private final LoanProductRepository loanProductRepository;

    public List<LoanProductListResponse> getLoanProducts() {
        return loanProductRepository.findAll()
                .stream()
                .map(product -> new LoanProductListResponse(
                        product.getId(),
                        product.getLoanProductName(),
                        product.getLoanProductDescription(),
                        product.getMinLimitAmount(),
                        product.getMaxLimitAmount(),
                        product.getRepaymentPeriodMonth(),
                        product.getMinInterestRate(),
                        product.getMaxInterestRate(),
                        product.getTargetCustomer(),
                        product.getLoanProductType(),
                        product.getRepaymentType()
                ))
                .toList();
    }

    public LoanProductDetailResponse getLoanProductDetail(Long loanProductId) {
        LoanProduct product = loanProductRepository.findById(loanProductId)
                .orElseThrow(() -> new IllegalArgumentException("대출 상품을 찾을 수 없습니다. id=" + loanProductId));

        return new LoanProductDetailResponse(
                product.getId(),
                product.getLoanProductName(),
                product.getLoanProductDescription(),
                product.getMinLimitAmount(),
                product.getMaxLimitAmount(),
                product.getRepaymentPeriodMonth(),
                product.getMinInterestRate(),
                product.getMaxInterestRate(),
                product.getTargetCustomer(),
                product.getLoanProductType(),
                product.getRepaymentType()

        );
    }
}