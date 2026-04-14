package com.nudgebank.bankbackend.deposit.config;

import com.nudgebank.bankbackend.deposit.domain.DepositProduct;
import com.nudgebank.bankbackend.deposit.domain.DepositProductRate;
import com.nudgebank.bankbackend.deposit.repository.DepositProductRateRepository;
import com.nudgebank.bankbackend.deposit.repository.DepositProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DepositProductInitializer implements CommandLineRunner {

    private final DepositProductRepository depositProductRepository;
    private final DepositProductRateRepository depositProductRateRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<DepositProductSeed> seeds = List.of(
            new DepositProductSeed(
                "정기예금",
                "FIXED_DEPOSIT",
                "안정적인 금리 운용과 만기까지 예치 혜택을 확인할 수 있는 목돈 예치형 상품입니다. 가입 즉시 금리가 확정되고 만기까지 안정적으로 운용할 수 있습니다.",
                new BigDecimal("100000.00"),
                null,
                3,
                36,
                List.of(
                    new RateSeed(3, 5, new BigDecimal("3.10")),
                    new RateSeed(6, 11, new BigDecimal("3.35")),
                    new RateSeed(12, 23, new BigDecimal("3.55")),
                    new RateSeed(24, 36, new BigDecimal("3.70"))
                )
            ),
            new DepositProductSeed(
                "정기적금",
                "FIXED_SAVING",
                "매월 일정 금액을 납입하며 만기까지 차곡차곡 모을 수 있는 정기적금 상품입니다. 회차별 납입과 자동이체를 함께 고려해 설계했습니다.",
                new BigDecimal("10000.00"),
                new BigDecimal("300000.00"),
                6,
                36,
                List.of(
                    new RateSeed(6, 11, new BigDecimal("3.60")),
                    new RateSeed(12, 23, new BigDecimal("3.90")),
                    new RateSeed(24, 36, new BigDecimal("4.20"))
                )
            )
        );

        for (DepositProductSeed seed : seeds) {
            DepositProduct product = depositProductRepository.findAll().stream()
                .filter(existing -> existing.getDepositProductType().equals(seed.depositProductType()))
                .findFirst()
                .map(existing -> {
                    existing.update(
                        seed.depositProductName(),
                        seed.depositProductDescription(),
                        seed.depositMinAmount(),
                        seed.depositMaxAmount(),
                        seed.minSavingMonth(),
                        seed.maxSavingMonth()
                    );
                    return existing;
                })
                .orElseGet(() -> depositProductRepository.save(
                    DepositProduct.builder()
                        .depositProductName(seed.depositProductName())
                        .depositProductType(seed.depositProductType())
                        .depositProductDescription(seed.depositProductDescription())
                        .depositMinAmount(seed.depositMinAmount())
                        .depositMaxAmount(seed.depositMaxAmount())
                        .minSavingMonth(seed.minSavingMonth())
                        .maxSavingMonth(seed.maxSavingMonth())
                        .build()
                ));

            for (RateSeed rateSeed : seed.rates()) {
                depositProductRateRepository
                    .findByDepositProduct_DepositProductIdAndMinSavingMonthAndMaxSavingMonth(
                        product.getDepositProductId(),
                        rateSeed.minSavingMonth(),
                        rateSeed.maxSavingMonth()
                    )
                    .ifPresentOrElse(
                        existingRate -> existingRate.update(rateSeed.interestRate()),
                        () -> depositProductRateRepository.save(
                            DepositProductRate.builder()
                                .depositProduct(product)
                                .minSavingMonth(rateSeed.minSavingMonth())
                                .maxSavingMonth(rateSeed.maxSavingMonth())
                                .interestRate(rateSeed.interestRate())
                                .build()
                        )
                    );
            }
        }
    }

    private record DepositProductSeed(
        String depositProductName,
        String depositProductType,
        String depositProductDescription,
        BigDecimal depositMinAmount,
        BigDecimal depositMaxAmount,
        Integer minSavingMonth,
        Integer maxSavingMonth,
        List<RateSeed> rates
    ) {
    }

    private record RateSeed(
        Integer minSavingMonth,
        Integer maxSavingMonth,
        BigDecimal interestRate
    ) {
    }
}
