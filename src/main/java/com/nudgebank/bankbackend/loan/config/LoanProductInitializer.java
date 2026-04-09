package com.nudgebank.bankbackend.loan.config;

import com.nudgebank.bankbackend.loan.domain.LoanProduct;
import com.nudgebank.bankbackend.loan.repository.LoanProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LoanProductInitializer implements CommandLineRunner {

    private final LoanProductRepository loanProductRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<LoanProductSeed> seeds = List.of(
            new LoanProductSeed(
                "SELF_DEVELOPMENT",
                "자기계발 대출",
                "자격증 및 역량개발 지원 대출",
                new BigDecimal("3.50"),
                new BigDecimal("5.50"),
                3_000_000L,
                500_000L,
                12,
                "EQUAL_INSTALLMENT"
            ),
            new LoanProductSeed(
                "CONSUMPTION_ANALYSIS",
                "소비분석 대출",
                "소비패턴 기반 맞춤 대출",
                new BigDecimal("4.00"),
                new BigDecimal("6.50"),
                5_000_000L,
                500_000L,
                12,
                "EQUAL_INSTALLMENT"
            ),
            new LoanProductSeed(
                "EMERGENCY",
                "긴급 대출",
                "긴급 생활안정 자금 대출",
                new BigDecimal("5.00"),
                new BigDecimal("8.00"),
                2_000_000L,
                300_000L,
                6,
                "EQUAL_INSTALLMENT"
            )
        );

        for (LoanProductSeed seed : seeds) {
            loanProductRepository.findByLoanProductType(seed.loanProductType())
                .ifPresentOrElse(
                    existing -> existing.updateProduct(
                        seed.loanProductName(),
                        seed.loanProductDescription(),
                        seed.minInterestRate(),
                        seed.maxInterestRate(),
                        seed.maxLimitAmount(),
                        seed.minLimitAmount(),
                        seed.repaymentPeriodMonth(),
                        seed.repaymentType()
                    ),
                    () -> loanProductRepository.save(
                        LoanProduct.builder()
                            .loanProductType(seed.loanProductType())
                            .loanProductName(seed.loanProductName())
                            .loanProductDescription(seed.loanProductDescription())
                            .minInterestRate(seed.minInterestRate())
                            .maxInterestRate(seed.maxInterestRate())
                            .maxLimitAmount(seed.maxLimitAmount())
                            .minLimitAmount(seed.minLimitAmount())
                            .repaymentPeriodMonth(seed.repaymentPeriodMonth())
                            .repaymentType(seed.repaymentType())
                            .createdAt(LocalDateTime.now())
                            .build()
                    )
                );
        }
    }

    private record LoanProductSeed(
        String loanProductType,
        String loanProductName,
        String loanProductDescription,
        BigDecimal minInterestRate,
        BigDecimal maxInterestRate,
        Long maxLimitAmount,
        Long minLimitAmount,
        Integer repaymentPeriodMonth,
        String repaymentType
    ) {}
}
