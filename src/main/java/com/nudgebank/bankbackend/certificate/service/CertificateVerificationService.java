package com.nudgebank.bankbackend.certificate.service;

import com.nudgebank.bankbackend.certificate.domain.CertificateMaster;
import com.nudgebank.bankbackend.certificate.domain.CertificateVerificationStatus;
import com.nudgebank.bankbackend.certificate.dto.CertificateMatchResult;
import com.nudgebank.bankbackend.certificate.exception.CertificateVerificationException;
import com.nudgebank.bankbackend.certificate.repository.CertificateMasterRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class CertificateVerificationService {

    private final CertificateMasterRepository certificateMasterRepository;

    public CertificateVerificationService(CertificateMasterRepository certificateMasterRepository) {
        this.certificateMasterRepository = certificateMasterRepository;
    }

    public CertificateMatchResult verify(Long certificateId, String extractedText) {
        CertificateMaster certificateMaster = certificateMasterRepository.findByCertificateIdAndIsActiveTrue(certificateId)
                .orElseThrow(() -> new CertificateVerificationException("Active certificate master not found"));

        String normalizedText = normalize(extractedText);
        boolean certificateNameMatched = contains(normalizedText, certificateMaster.getCertificateName());
        boolean issuerNameMatched = contains(normalizedText, certificateMaster.getIssuerName());

        if (certificateNameMatched && issuerNameMatched) {
            return new CertificateMatchResult(
                    CertificateVerificationStatus.VERIFIED,
                    OffsetDateTime.now()
            );
        }

        return new CertificateMatchResult(
                CertificateVerificationStatus.VERIFICATION_FAILED,
                null
        );
    }

    private boolean contains(String normalizedText, String expectedValue) {
        return normalizedText.contains(normalize(expectedValue));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);

        return normalized.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]", "");
    }
}
