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
                "목돈을 한 번에 예치하고 만기까지 유지하는 기본형 예금 상품입니다.",
                new BigDecimal("10000.00"),
                null,
                1,
                36,
                List.of(
                    new RateSeed(1, 6, new BigDecimal("2.80")),
                    new RateSeed(7, 12, new BigDecimal("3.10")),
                    new RateSeed(13, 24, new BigDecimal("3.25")),
                    new RateSeed(25, 36, new BigDecimal("3.35"))
                )
            ),
            new DepositProductSeed(
                "정기적금",
                "FIXED_SAVING",
                "매월 일정 금액을 납입하며 목표 자금을 만들어가는 적금 상품입니다.",
                new BigDecimal("10000.00"),
                new BigDecimal("1000000.00"),
                6,
                36,
                List.of(
                    new RateSeed(6, 11, new BigDecimal("3.10")),
                    new RateSeed(12, 23, new BigDecimal("3.40")),
                    new RateSeed(24, 36, new BigDecimal("3.60"))
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
