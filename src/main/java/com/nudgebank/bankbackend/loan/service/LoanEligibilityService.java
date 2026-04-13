package com.nudgebank.bankbackend.loan.service;

import com.nudgebank.bankbackend.credit.domain.CreditHistory;
import com.nudgebank.bankbackend.credit.repository.CreditHistoryRepository;
import com.nudgebank.bankbackend.loan.domain.LoanProduct;
import com.nudgebank.bankbackend.loan.dto.LoanEligibilityRequest;
import com.nudgebank.bankbackend.loan.dto.LoanEligibilityResponse;
import com.nudgebank.bankbackend.loan.repository.LoanProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanEligibilityService {

    private static final int MIN_ELIGIBLE_CREDIT_SCORE = 500;
    private static final String SELF_DEVELOPMENT_TYPE = "SELF_DEVELOPMENT";
    private static final String CONSUMPTION_ANALYSIS_TYPE = "CONSUMPTION_ANALYSIS";
    private static final String EMERGENCY_TYPE = "EMERGENCY";

    private final CreditHistoryRepository creditHistoryRepository;
    private final LoanProductRepository loanProductRepository;

    public LoanEligibilityResponse check(Long memberId, LoanEligibilityRequest request) {
        if (memberId == null) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }
        if (request == null || request.productKey() == null || request.productKey().isBlank()) {
            throw new IllegalArgumentException("INVALID_PRODUCT_KEY");
        }

        String loanProductType = toLoanProductType(request.productKey());
        LoanProduct product = loanProductRepository.findByLoanProductType(loanProductType)
                .orElseThrow(() -> new EntityNotFoundException("대출 상품을 찾을 수 없습니다. type=" + loanProductType));

        CreditHistory creditHistory = creditHistoryRepository
                .findTopByMemberIdOrderByEvaluatedAtDescCreditHistoryIdDesc(memberId)
                .orElseThrow(() -> new EntityNotFoundException("대출 가능 여부 판단을 위한 신용 정보가 없습니다."));

        int creditScore = creditHistory.getCreditScore() != null ? creditHistory.getCreditScore() : 0;

        List<String> reasons = new ArrayList<>();
        boolean eligible = true;

        if (creditScore < MIN_ELIGIBLE_CREDIT_SCORE) {
            eligible = false;
            reasons.add("신용점수가 내부 기준 500점 미만입니다.");
        } else {
            reasons.add("신용점수가 내부 기준 이상입니다.");
        }

        Long maxLimitAmount = product.getMaxLimitAmount() != null ? product.getMaxLimitAmount() : 0L;

        return new LoanEligibilityResponse(
                eligible,
                eligible ? "APPROVED" : "REJECTED",
                creditScore,
                request.productKey(),
                reasons
        );
    }

    private String toLoanProductType(String productKey) {
        return switch (productKey) {
            case "youth-loan" -> SELF_DEVELOPMENT_TYPE;
            case "consumption-loan" -> CONSUMPTION_ANALYSIS_TYPE;
            case "situate-loan" -> EMERGENCY_TYPE;
            default -> throw new IllegalArgumentException("지원하지 않는 상품입니다. productKey=" + productKey);
        };
    }
}
