package com.nudgebank.bankbackend.finance.config;

import com.nudgebank.bankbackend.finance.domain.AgeGroupBaseline;
import com.nudgebank.bankbackend.finance.repository.AgeGroupBaselineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AgeGroupBaselineInitializer implements CommandLineRunner {

    private final AgeGroupBaselineRepository ageGroupBaselineRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (ageGroupBaselineRepository.count() > 0) {
            return;
        }

        List<AgeGroupBaselineSeed> seeds = List.of(
                new AgeGroupBaselineSeed("50s", "581983.53", "0.4976", "0.3722", "0.1194", "0.0108", "6008988000.00", "2.0700", "안정형"),
                new AgeGroupBaselineSeed("60s+", "570748.59", "0.5253", "0.3530", "0.1115", "0.0102", "5345730000.00", "1.8400", "안정형"),
                new AgeGroupBaselineSeed("40s", "454551.23", "0.4817", "0.3725", "0.1317", "0.0141", "4285018000.00", "1.4700", "안정형"),
                new AgeGroupBaselineSeed("30s", "229029.07", "0.4290", "0.3668", "0.1778", "0.0264", "1768664000.00", "0.6100", "기본"),
                new AgeGroupBaselineSeed("20s", "35169.57", "0.3016", "0.4124", "0.2697", "0.0163", "38268610.00", "0.0100", "기본"),
                new AgeGroupBaselineSeed("10s", "5247.12", "0.5140", "0.3375", "0.1438", "0.0047", "15962.07", "0.0000", "안정형")
        );

        for (AgeGroupBaselineSeed seed : seeds) {
            ageGroupBaselineRepository.save(
                    new AgeGroupBaseline(
                            seed.ageGroup(),
                            seed.avgSpendingValue(),
                            seed.essentialRatioValue(),
                            seed.normalRatioValue(),
                            seed.discretionaryRatioValue(),
                            seed.riskRatioValue(),
                            seed.volatilityValue(),
                            seed.volatilityIndexValue(),
                            seed.repaymentAction()
                    )
            );
        }
    }

    private record AgeGroupBaselineSeed(
            String ageGroup,
            String avgSpending,
            String essentialRatio,
            String normalRatio,
            String discretionaryRatio,
            String riskRatio,
            String volatility,
            String volatilityIndex,
            String repaymentAction
    ) {
        private BigDecimal avgSpendingValue() {
            return new BigDecimal(avgSpending);
        }

        private BigDecimal essentialRatioValue() {
            return new BigDecimal(essentialRatio);
        }

        private BigDecimal normalRatioValue() {
            return new BigDecimal(normalRatio);
        }

        private BigDecimal discretionaryRatioValue() {
            return new BigDecimal(discretionaryRatio);
        }

        private BigDecimal riskRatioValue() {
            return new BigDecimal(riskRatio);
        }

        private BigDecimal volatilityValue() {
            return new BigDecimal(volatility);
        }

        private BigDecimal volatilityIndexValue() {
            return new BigDecimal(volatilityIndex);
        }
    }
}
