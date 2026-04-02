package com.nudgebank.bankbackend.certificate.service;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.certificate.domain.CertificateIssuerAlias;
import com.nudgebank.bankbackend.certificate.domain.CertificateMaster;
import com.nudgebank.bankbackend.certificate.domain.CertificateVerificationStatus;
import com.nudgebank.bankbackend.certificate.dto.CertificateMatchResult;
import com.nudgebank.bankbackend.certificate.exception.CertificateVerificationException;
import com.nudgebank.bankbackend.certificate.repository.CertificateIssuerAliasRepository;
import com.nudgebank.bankbackend.certificate.repository.CertificateMasterRepository;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CertificateVerificationService {

    private static final Pattern NAME_PATTERN = Pattern.compile(
            "(?:성\\s*명|이\\s*름)\\s*[:：]?\\s*([가-힣]{2,10})"
    );
    private static final Pattern NAME_FALLBACK_PATTERN = Pattern.compile(
            "(?:성\\s*명|이\\s*름)\\s*[:：]?\\s*([가-힣\\s]{2,20})"
    );

    private static final String[] PASS_KEYWORDS = {
            "합격",
            "합격증",
            "합격증명서",
            "취득",
            "증명",
            "확인",
            "확인서",
            "자격취득확인서",
            "취특"
    };

    private static final String[] REJECTION_KEYWORDS = {
            "예시",
            "샘플",
            "수험표",
            "접수확인"
    };

    private final CertificateMasterRepository certificateMasterRepository;
    private final CertificateIssuerAliasRepository certificateIssuerAliasRepository;

    public CertificateVerificationService(
            CertificateMasterRepository certificateMasterRepository,
            CertificateIssuerAliasRepository certificateIssuerAliasRepository
    ) {
        this.certificateMasterRepository = certificateMasterRepository;
        this.certificateIssuerAliasRepository = certificateIssuerAliasRepository;
    }

    public CertificateMatchResult verify(
            Member member,
            Long certificateId,
            String extractedText,
            List<String> extractedLines
    ) {
        CertificateMaster certificateMaster = certificateMasterRepository.findByCertificateIdAndIsActiveTrue(certificateId)
                .orElseThrow(() -> new CertificateVerificationException("Active certificate master not found"));

        String normalizedText = normalize(extractedText);
        List<CertificateIssuerAlias> issuerAliases =
                certificateIssuerAliasRepository.findAllByCertificateIdAndIsActiveTrue(certificateId);

        String detectedName = detectName(extractedText, extractedLines);
        boolean nameMatched = matchesName(member.getName(), detectedName, normalizedText);
        boolean certificateNameMatched = contains(normalizedText, certificateMaster.getCertificateName())
                || contains(normalizedText, certificateMaster.getCertificateNameEn());
        String matchedIssuerName = findMatchedIssuerName(normalizedText, issuerAliases);
        boolean issuerMatched = matchedIssuerName != null;
        boolean passKeywordMatched = containsAny(normalizedText, PASS_KEYWORDS);
        boolean rejectionKeywordMatched = containsAny(normalizedText, REJECTION_KEYWORDS);

        BigDecimal score = BigDecimal.ZERO;
        if (certificateNameMatched) {
            score = score.add(BigDecimal.valueOf(40));
        }
        if (issuerMatched) {
            score = score.add(BigDecimal.valueOf(30));
        }
        if (nameMatched) {
            score = score.add(BigDecimal.valueOf(20));
        }
        if (passKeywordMatched) {
            score = score.add(BigDecimal.valueOf(10));
        }
        if (rejectionKeywordMatched) {
            score = score.subtract(BigDecimal.valueOf(100));
        }

        if (nameMatched && certificateNameMatched && issuerMatched && passKeywordMatched && !rejectionKeywordMatched) {
            return new CertificateMatchResult(
                    CertificateVerificationStatus.VERIFIED,
                    OffsetDateTime.now(),
                    score,
                    matchedIssuerName,
                    detectedName,
                    true,
                    "VERIFIED"
            );
        }

        return new CertificateMatchResult(
                CertificateVerificationStatus.VERIFICATION_FAILED,
                null,
                score,
                matchedIssuerName,
                detectedName,
                nameMatched,
                buildReviewNote(
                        nameMatched,
                        certificateNameMatched,
                        issuerMatched,
                        passKeywordMatched,
                        rejectionKeywordMatched
                )
        );
    }

    private String buildReviewNote(
            boolean nameMatched,
            boolean certificateNameMatched,
            boolean issuerMatched,
            boolean passKeywordMatched,
            boolean rejectionKeywordMatched
    ) {
        if (!nameMatched) {
            return "NAME_MISMATCH";
        }
        if (!certificateNameMatched) {
            return "CERTIFICATE_NAME_MISMATCH";
        }
        if (!issuerMatched) {
            return "ISSUER_MISMATCH";
        }
        if (!passKeywordMatched) {
            return "PASS_KEYWORD_NOT_FOUND";
        }
        if (rejectionKeywordMatched) {
            return "INVALID_DOCUMENT_TYPE";
        }
        return "VERIFICATION_FAILED";
    }

    private String findMatchedIssuerName(String normalizedText, List<CertificateIssuerAlias> issuerAliases) {
        for (CertificateIssuerAlias issuerAlias : issuerAliases) {
            if (matchesIssuer(normalizedText, issuerAlias.getIssuerName())
                    || matchesIssuer(normalizedText, issuerAlias.getIssuerNameEn())) {
                return issuerAlias.getIssuerName();
            }
        }
        return null;
    }

    private boolean matchesIssuer(String normalizedText, String issuerName) {
        if (contains(normalizedText, issuerName)) {
            return true;
        }

        String normalizedIssuerName = normalize(issuerName);
        if (normalizedIssuerName.isBlank()) {
            return false;
        }

        return findApproximateMatch(normalizedText, normalizedIssuerName, 2);
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

        return hasAllowedMismatch(normalizedMemberName, normalizedDetectedName, 1);
    }

    private boolean findApproximateMatch(String normalizedText, String expectedValue, int allowedMismatch) {
        if (normalizedText.length() < expectedValue.length()) {
            return false;
        }

        for (int start = 0; start <= normalizedText.length() - expectedValue.length(); start++) {
            String candidate = normalizedText.substring(start, start + expectedValue.length());
            if (hasAllowedMismatch(expectedValue, candidate, allowedMismatch)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasAllowedMismatch(String expectedValue, String actualValue, int allowedMismatch) {
        if (expectedValue.isBlank() || actualValue.isBlank()) {
            return false;
        }

        if (expectedValue.length() != actualValue.length()) {
            return false;
        }

        int mismatchCount = 0;
        for (int i = 0; i < expectedValue.length(); i++) {
            if (expectedValue.charAt(i) != actualValue.charAt(i)) {
                mismatchCount++;
                if (mismatchCount > allowedMismatch) {
                    return false;
                }
            }
        }

        return mismatchCount <= allowedMismatch;
    }

    private String detectName(String extractedText, List<String> extractedLines) {
        if (extractedLines != null) {
            for (String line : extractedLines) {
                String detected = extractNameFromText(line);
                if (detected != null) {
                    return detected;
                }
            }
        }

        return extractNameFromText(extractedText);
    }

    private String extractNameFromText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        Matcher matcher = NAME_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }

        Matcher fallbackMatcher = NAME_FALLBACK_PATTERN.matcher(value);
        if (fallbackMatcher.find()) {
            String candidate = fallbackMatcher.group(1)
                    .replaceAll("\\s+", "")
                    .replaceAll("[^가-힣]", "");
            if (candidate.length() >= 2) {
                return candidate;
            }
        }

        return null;
    }

    private boolean containsAny(String normalizedText, String... expectedValues) {
        for (String expectedValue : expectedValues) {
            if (contains(normalizedText, expectedValue)) {
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
