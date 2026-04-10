package com.nudgebank.bankbackend.certificate.config;

import com.nudgebank.bankbackend.certificate.domain.CertificateMaster;
import com.nudgebank.bankbackend.certificate.repository.CertificateMasterRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CertificateMasterInitializer implements CommandLineRunner {

    private final CertificateMasterRepository certificateMasterRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<CertificateSeed> seeds = List.of(
            new CertificateSeed(1L, "컴퓨터활용능력 1급", "대한상공회의소", new BigDecimal("0.20")),
            new CertificateSeed(2L, "컴퓨터활용능력 2급", "대한상공회의소", new BigDecimal("0.10")),
            new CertificateSeed(3L, "워드프로세서", "대한상공회의소", new BigDecimal("0.10")),
            new CertificateSeed(4L, "한국사능력검정시험", "국사편찬위원회", new BigDecimal("0.10")),
            new CertificateSeed(5L, "정보처리기사", "한국산업인력공단", new BigDecimal("0.30")),
            new CertificateSeed(6L, "정보처리산업기사", "한국산업인력공단", new BigDecimal("0.20")),
            new CertificateSeed(7L, "ADsP", "한국데이터산업진흥원", new BigDecimal("0.20")),
            new CertificateSeed(8L, "SQLD", "한국데이터산업진흥원", new BigDecimal("0.20")),
            new CertificateSeed(9L, "빅데이터분석기사", "한국데이터산업진흥원", new BigDecimal("0.30")),
            new CertificateSeed(10L, "전산회계 1급", "한국세무사회", new BigDecimal("0.20")),
            new CertificateSeed(11L, "전산세무 2급", "한국세무사회", new BigDecimal("0.20")),
            new CertificateSeed(12L, "투자자산운용사", "한국금융투자협회", new BigDecimal("0.30")),
            new CertificateSeed(13L, "AFPK", "한국FPSB", new BigDecimal("0.30")),
            new CertificateSeed(14L, "신용분석사", "한국금융연수원", new BigDecimal("0.30")),
            new CertificateSeed(18L, "JLPT", "국제교류기금", new BigDecimal("0.10")),
            new CertificateSeed(19L, "HSK", "중국국가한판", new BigDecimal("0.10")),
            new CertificateSeed(20L, "공인중개사", "한국산업인력공단", new BigDecimal("0.30")),
            new CertificateSeed(21L, "감정평가사", "한국감정평가사협회", new BigDecimal("0.40")),
            new CertificateSeed(22L, "세무사", "한국산업인력공단", new BigDecimal("0.40")),
            new CertificateSeed(23L, "공인회계사", "금융감독원", new BigDecimal("0.50")),
            new CertificateSeed(24L, "변호사", "대한변호사협회", new BigDecimal("0.50")),
            new CertificateSeed(25L, "전기기사", "한국산업인력공단", new BigDecimal("0.25")),
            new CertificateSeed(26L, "산업안전기사", "한국산업인력공단", new BigDecimal("0.25")),
            new CertificateSeed(27L, "건축기사", "한국산업인력공단", new BigDecimal("0.25")),
            new CertificateSeed(28L, "토목기사", "한국산업인력공단", new BigDecimal("0.25")),
            new CertificateSeed(29L, "기계기사", "한국산업인력공단", new BigDecimal("0.25"))
        );

        for (CertificateSeed seed : seeds) {
            certificateMasterRepository.findById(seed.certificateId())
                .ifPresentOrElse(
                    existing -> existing.updateMaster(
                        seed.certificateName(),
                        seed.issuerName(),
                        seed.rateDiscount(),
                        true
                    ),
                    () -> certificateMasterRepository.save(
                        CertificateMaster.create(
                            seed.certificateId(),
                            seed.certificateName(),
                            seed.issuerName(),
                            seed.rateDiscount(),
                            true,
                            OffsetDateTime.now()
                        )
                    )
                );
        }
    }

    private record CertificateSeed(
        Long certificateId,
        String certificateName,
        String issuerName,
        BigDecimal rateDiscount
    ) {}
}
