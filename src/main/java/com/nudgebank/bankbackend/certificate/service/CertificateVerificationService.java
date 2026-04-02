package com.nudgebank.bankbackend.certificate.service;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.certificate.domain.CertificateMaster;
import com.nudgebank.bankbackend.certificate.domain.CertificateVerificationStatus;
import com.nudgebank.bankbackend.certificate.dto.CertificateMatchResult;
import com.nudgebank.bankbackend.certificate.exception.CertificateVerificationException;
import com.nudgebank.bankbackend.certificate.repository.CertificateMasterRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CertificateVerificationService {

    private static final Pattern NAME_PATTERN = Pattern.compile(
            "(?:성\\s*명|이\\s*름)\\s*[:：`\"' ]*\\s*([가-힣]{2,10})"
    );
    private static final String[] PASS_KEYWORDS = {
            "합격", "합격증", "합격증명서", "취득", "증명", "확인", "확인서"
    };
    private static final String[] INVALID_DOCUMENT_KEYWORDS = {
            "예시", "샘플", "수험표", "접수확인"
    };

    private final CertificateMasterRepository certificateMasterRepository;

    public CertificateVerificationService(CertificateMasterRepository certificateMasterRepository) {
        this.certificateMasterRepository = certificateMasterRepository;
    }

    public CertificateMatchResult verify(Long certificateId, String extractedText, Member member) {
        CertificateMaster certificateMaster = certificateMasterRepository.findByCertificateIdAndIsActiveTrue(certificateId)
                .orElseThrow(() -> new CertificateVerificationException("Active certificate master not found"));

        if (extractedText == null || extractedText.isBlank()) {
            return new CertificateMatchResult(
                    CertificateVerificationStatus.VERIFICATION_FAILED,
                    null,
                    "OCR_TEXT_NOT_DETECTED"
            );
        }

        String normalizedText = normalize(extractedText);
        String detectedName = extractName(extractedText);
        boolean nameMatched = matchesName(member.getName(), detectedName, normalizedText);
        boolean certificateNameMatched = contains(normalizedText, certificateMaster.getCertificateName());
        boolean issuerNameMatched = contains(normalizedText, certificateMaster.getIssuerName());
        boolean passKeywordMatched = containsAny(normalizedText, PASS_KEYWORDS);
        boolean invalidDocumentMatched = containsAny(normalizedText, INVALID_DOCUMENT_KEYWORDS);

        if (nameMatched && certificateNameMatched && issuerNameMatched && passKeywordMatched && !invalidDocumentMatched) {
            return new CertificateMatchResult(
                    CertificateVerificationStatus.VERIFIED,
                    OffsetDateTime.now(),
                    null
            );
        }

        String failureReason = buildFailureReason(
                nameMatched,
                certificateNameMatched,
                issuerNameMatched,
                passKeywordMatched,
                invalidDocumentMatched
        );

        return new CertificateMatchResult(
                CertificateVerificationStatus.VERIFICATION_FAILED,
                null,
                failureReason
        );
    }

    private String buildFailureReason(
            boolean nameMatched,
            boolean certificateNameMatched,
            boolean issuerNameMatched,
            boolean passKeywordMatched,
            boolean invalidDocumentMatched
    ) {
        if (!nameMatched) {
            return "NAME_MISMATCH";
        }
        if (!certificateNameMatched) {
            return "CERTIFICATE_NAME_MISMATCH";
        }
        if (!issuerNameMatched) {
            return "ISSUER_NAME_MISMATCH";
        }
        if (!passKeywordMatched) {
            return "PASS_KEYWORD_NOT_FOUND";
        }
        if (invalidDocumentMatched) {
            return "INVALID_DOCUMENT_TYPE";
        }
        return "VERIFICATION_FAILED";
    }

    private String extractName(String extractedText) {
        Matcher matcher = NAME_PATTERN.matcher(extractedText);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }

    private boolean matchesName(String memberName, String detectedName, String normalizedText) {
        if (contains(normalizedText, memberName)) {
            return true;
        }
        if (detectedName == null || detectedName.isBlank()) {
            return false;
        }

        String normalizedMemberName = normalize(memberName);
        String normalizedDetectedName = normalize(detectedName);
        if (normalizedMemberName.equals(normalizedDetectedName)) {
            return true;
        }

        return hasSingleCharacterMismatch(normalizedMemberName, normalizedDetectedName);
    }

    private boolean hasSingleCharacterMismatch(String expected, String actual) {
        if (expected.length() != actual.length() || expected.isBlank()) {
            return false;
        }

        int mismatchCount = 0;
        for (int i = 0; i < expected.length(); i++) {
            if (expected.charAt(i) != actual.charAt(i)) {
                mismatchCount++;
                if (mismatchCount > 1) {
                    return false;
                }
            }
        }

        return mismatchCount == 1;
    }

    private boolean containsAny(String normalizedText, String... values) {
        for (String value : values) {
            if (contains(normalizedText, value)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String normalizedText, String expectedValue) {
        String normalizedExpectedValue = normalize(expectedValue);
        if (normalizedExpectedValue.isBlank()) {
            return false;
        }

        return normalizedText.contains(normalizedExpectedValue);
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
