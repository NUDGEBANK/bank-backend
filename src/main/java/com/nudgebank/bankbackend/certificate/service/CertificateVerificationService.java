package com.nudgebank.bankbackend.certificate.service;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.certificate.domain.CertificateMaster;
import com.nudgebank.bankbackend.certificate.domain.CertificateIssuerAlias;
import com.nudgebank.bankbackend.certificate.domain.CertificateVerificationStatus;
import com.nudgebank.bankbackend.certificate.dto.CertificateMatchResult;
import com.nudgebank.bankbackend.certificate.exception.CertificateVerificationException;
import com.nudgebank.bankbackend.certificate.repository.CertificateIssuerAliasRepository;
import com.nudgebank.bankbackend.certificate.repository.CertificateMasterRepository;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CertificateVerificationService {

    private static final Pattern NAME_PATTERN =
            Pattern.compile("(?:\\uC131\\uBA85|\\uC774\\uB984)\\s*[:\\uFF1A]?\\s*([\\uAC00-\\uD7A3]{2,10})");

    private static final Pattern[] DATE_PATTERNS = {
            Pattern.compile("(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})"),
            Pattern.compile("(20\\d{2})\\s*\\uB144\\s*(\\d{1,2})\\s*\\uC6D4\\s*(\\d{1,2})\\s*\\uC77C")
    };

    private static final Pattern LABELED_DATE_PATTERN = Pattern.compile(
            "(\\uCDE8\\uB4DD\\uC77C|\\uCDE8\\uB4DD\\uC77C\\uC790|\\uD569\\uACA9\\uC77C|\\uD569\\uACA9\\uC77C\\uC790|\\uD569\\uACA9\\uC5F0\\uC6D4\\uC77C|\\uBC1C\\uAE09\\uC77C|\\uBC1C\\uAE09\\uC5F0\\uC6D4\\uC77C|\\uC790\\uACA9\\uCDE8\\uB4DD\\uC77C|\\uC790\\uACA9\\uC99D\\uCDE8\\uB4DD\\uC77C|\\uC790\\uACA9\\uC744\\uCDE8\\uB4DD).{0,30}?(20\\d{2})\\uB144(\\d{1,2})\\uC6D4(\\d{1,2})\\uC77C"
    );

    private static final String[] DATE_LABELS = {
            "\uCDE8\uB4DD\uC77C",
            "\uCDE8\uB4DD\uC77C\uC790",
            "\uD569\uACA9\uC77C",
            "\uD569\uACA9\uC77C\uC790",
            "\uD569\uACA9\uC5F0\uC6D4\uC77C",
            "\uBC1C\uAE09\uC77C",
            "\uBC1C\uAE09\uC5F0\uC6D4\uC77C",
            "\uC790\uACA9\uCDE8\uB4DD\uC77C",
            "\uC790\uACA9\uC99D\uCDE8\uB4DD\uC77C",
            "\uC790\uACA9\uC744\uCDE8\uB4DD"
    };

    private static final String[] DATE_CONTEXT_KEYWORDS = {
            "\uCDE8\uB4DD",
            "\uD569\uACA9",
            "\uC790\uACA9\uC744\uCDE8\uB4DD",
            "\uD569\uACA9\uD558\uC600\uC74C",
            "\uD655\uC778\uD569\uB2C8\uB2E4"
    };

    private static final String[] DATE_EXCLUDE_KEYWORDS = {
            "\uC720\uD6A8\uAE30\uAC04",
            "\uC0DD\uB144\uC6D4\uC77C",
            "\uCD9C\uB825\uC77C",
            "\uBC1C\uAE09\uBC88\uD638"
    };

    private static final String[] PASS_KEYWORDS = {
            "\uD569\uACA9",
            "\uD569\uACA9\uC99D",
            "\uD569\uACA9\uC778\uC99D\uC11C",
            "\uCDE8\uB4DD",
            "\uC778\uC99D",
            "\uD655\uC778",
            "\uC790\uACA9\uCDE8\uB4DD"
    };

    private static final String[] INVALID_DOCUMENT_KEYWORDS = {
            "\uC608\uC2DC",
            "\uC0D8\uD50C",
            "\uC0D8\uD50C\uBB38\uC11C",
            "\uC811\uC218\uD655\uC778"
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

    public CertificateMatchResult verify(Long certificateId, String extractedText, Member member) {
        CertificateMaster certificateMaster = certificateMasterRepository.findByCertificateIdAndIsActiveTrue(certificateId)
                .orElseThrow(() -> new CertificateVerificationException("\uD65C\uC131\uD654\uB41C \uC790\uACA9\uC99D \uAE30\uC900 \uC815\uBCF4\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4."));

        if (extractedText == null || extractedText.isBlank()) {
            return new CertificateMatchResult(
                    CertificateVerificationStatus.VERIFICATION_FAILED,
                    null,
                    "OCR_TEXT_NOT_DETECTED"
            );
        }

        String normalizedText = normalize(extractedText);
        String detectedName = extractName(extractedText);
        boolean nameMatched = matchesName(member.getName(), detectedName, normalizedText, extractedText);
        boolean certificateNameMatched = contains(normalizedText, certificateMaster.getCertificateName());
        boolean issuerNameMatched = matchesIssuer(certificateId, certificateMaster.getIssuerName(), normalizedText);
        boolean passKeywordMatched = containsAny(normalizedText, PASS_KEYWORDS);
        boolean invalidDocumentMatched = containsAny(normalizedText, INVALID_DOCUMENT_KEYWORDS);

        if (nameMatched && certificateNameMatched && issuerNameMatched && passKeywordMatched && !invalidDocumentMatched) {
            return new CertificateMatchResult(
                    CertificateVerificationStatus.VERIFIED,
                    OffsetDateTime.now(),
                    null
            );
        }

        return new CertificateMatchResult(
                CertificateVerificationStatus.VERIFICATION_FAILED,
                null,
                buildFailureReason(
                        nameMatched,
                        certificateNameMatched,
                        issuerNameMatched,
                        passKeywordMatched,
                        invalidDocumentMatched
                )
        );
    }

    public LocalDate extractCertificateDate(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return null;
        }

        String compactText = compactForDateExtraction(extractedText);
        LocalDate labeledDate = extractDateFromLabeledPattern(compactText);
        if (labeledDate != null) {
            return labeledDate;
        }

        LocalDate dateFromLabeledText = extractDateNearLabels(compactText);
        if (dateFromLabeledText != null) {
            return dateFromLabeledText;
        }

        LocalDate dateFromContext = extractDateNearContext(compactText);
        if (dateFromContext != null) {
            return dateFromContext;
        }

        return extractFallbackCertificateDate(compactText);
    }

    private LocalDate extractDateFromLabeledPattern(String compactText) {
        Matcher matcher = LABELED_DATE_PATTERN.matcher(compactText);
        if (!matcher.find()) {
            return null;
        }

        try {
            return LocalDate.of(
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4))
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private LocalDate extractDateFromText(String text) {
        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                try {
                    return LocalDate.of(
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(3))
                    );
                } catch (RuntimeException ignored) {
                    // Continue scanning.
                }
            }
        }
        return null;
    }

    private LocalDate extractFallbackCertificateDate(String compactText) {
        List<LocalDate> candidates = new ArrayList<>();

        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(compactText);
            while (matcher.find()) {
                int start = Math.max(0, matcher.start() - 20);
                int end = Math.min(compactText.length(), matcher.end() + 20);
                String context = compactText.substring(start, end);

                if (containsAnyKeyword(context, DATE_EXCLUDE_KEYWORDS)) {
                    continue;
                }

                try {
                    candidates.add(LocalDate.of(
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(3))
                    ));
                } catch (RuntimeException ignored) {
                    // Continue scanning.
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        return candidates.stream()
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalDate extractDateNearLabels(String compactText) {
        for (String label : DATE_LABELS) {
            int labelIndex = compactText.indexOf(label);
            if (labelIndex < 0) {
                continue;
            }

            int start = labelIndex + label.length();
            int end = Math.min(compactText.length(), start + 40);
            LocalDate extractedDate = extractDateFromText(compactText.substring(start, end));
            if (extractedDate != null) {
                return extractedDate;
            }
        }

        return null;
    }

    private LocalDate extractDateNearContext(String compactText) {
        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(compactText);
            while (matcher.find()) {
                int start = Math.max(0, matcher.start() - 30);
                int end = Math.min(compactText.length(), matcher.end() + 30);
                String context = compactText.substring(start, end);

                if (containsAnyKeyword(context, DATE_EXCLUDE_KEYWORDS)) {
                    continue;
                }
                if (!containsAnyKeyword(context, DATE_CONTEXT_KEYWORDS)) {
                    continue;
                }

                try {
                    return LocalDate.of(
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(3))
                    );
                } catch (RuntimeException ignored) {
                    // Continue scanning.
                }
            }
        }

        return null;
    }

    private boolean containsAnyKeyword(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String compactForDateExtraction(String text) {
        return text
                .replaceAll("\\s+", "")
                .replace("\u3000", "");
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
            String compactText = compactForDateExtraction(extractedText);
            Matcher compactMatcher = Pattern.compile("(?:\\uC131\\uBA85|\\uC774\\uB984)([\\uAC00-\\uD7A3]{2,10})").matcher(compactText);
            if (!compactMatcher.find()) {
                return null;
            }
            return compactMatcher.group(1);
        }
        return matcher.group(1);
    }

    private boolean matchesName(String memberName, String detectedName, String normalizedText, String extractedText) {
        if (contains(normalizedText, memberName)) {
            return true;
        }

        String normalizedMemberName = normalize(memberName);
        if (containsNameWithSingleCharacterMismatch(extractedText, normalizedMemberName)) {
            return true;
        }

        if (detectedName == null || detectedName.isBlank()) {
            return false;
        }

        String normalizedDetectedName = normalize(detectedName);
        if (normalizedMemberName.equals(normalizedDetectedName)) {
            return true;
        }

        return hasLabelBasedNameMismatch(normalizedMemberName, normalizedDetectedName);
    }

    private boolean containsNameWithSingleCharacterMismatch(String extractedText, String normalizedMemberName) {
        String koreanOnlyText = extractKoreanOnly(extractedText);
        int targetLength = normalizedMemberName.length();
        if (targetLength == 0 || koreanOnlyText.length() < targetLength) {
            return false;
        }

        boolean hasNameLabel = hasNameLabel(extractedText);

        for (int index = 0; index <= koreanOnlyText.length() - targetLength; index++) {
            String candidate = koreanOnlyText.substring(index, index + targetLength);
            if (normalizedMemberName.equals(candidate)
                || hasSingleCharacterMismatch(normalizedMemberName, candidate)
                || (hasNameLabel && hasLabelBasedNameMismatch(normalizedMemberName, candidate))) {
                return true;
            }
        }

        return false;
    }

    private String extractKoreanOnly(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[^\\uAC00-\\uD7A3]", "");
    }

    private boolean hasLabelBasedNameMismatch(String expected, String actual) {
        int mismatchThreshold = expected.length() <= 3 ? 2 : 1;
        return hasCharacterMismatchWithinThreshold(expected, actual, mismatchThreshold);
    }

    private boolean hasSingleCharacterMismatch(String expected, String actual) {
        return hasCharacterMismatchWithinThreshold(expected, actual, 1);
    }

    private boolean hasNameLabel(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return false;
        }

        String compactText = compactForDateExtraction(extractedText);
        return compactText.contains("\uC131\uBA85") || compactText.contains("\uC774\uB984");
    }

    private boolean hasCharacterMismatchWithinThreshold(String expected, String actual, int threshold) {
        if (expected.length() != actual.length() || expected.isBlank()) {
            return false;
        }

        int mismatchCount = 0;
        for (int index = 0; index < expected.length(); index++) {
            if (expected.charAt(index) != actual.charAt(index)) {
                mismatchCount++;
                if (mismatchCount > threshold) {
                    return false;
                }
            }
        }

        return mismatchCount > 0;
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

    private boolean matchesIssuer(Long certificateId, String issuerName, String normalizedText) {
        if (contains(normalizedText, issuerName)) {
            return true;
        }

        String simplifiedIssuerName = simplifyIssuerName(issuerName);
        if (contains(normalizedText, simplifiedIssuerName)) {
            return true;
        }
        if (containsWithSingleCharacterMismatch(normalizedText, simplifiedIssuerName)) {
            return true;
        }

        for (CertificateIssuerAlias alias : certificateIssuerAliasRepository.findAllByCertificateIdAndIsActiveTrue(certificateId)) {
            if (contains(normalizedText, alias.getIssuerName())) {
                return true;
            }
            if (contains(normalizedText, simplifyIssuerName(alias.getIssuerName()))) {
                return true;
            }
            if (containsWithSingleCharacterMismatch(normalizedText, simplifyIssuerName(alias.getIssuerName()))) {
                return true;
            }
            if (contains(normalizedText, alias.getIssuerNameEn())) {
                return true;
            }
        }

        return false;
    }

    private boolean containsWithSingleCharacterMismatch(String normalizedText, String expectedValue) {
        String normalizedExpectedValue = normalize(expectedValue);
        int targetLength = normalizedExpectedValue.length();
        if (normalizedExpectedValue.isBlank() || normalizedText.length() < targetLength) {
            return false;
        }

        for (int index = 0; index <= normalizedText.length() - targetLength; index++) {
            String candidate = normalizedText.substring(index, index + targetLength);
            if (normalizedExpectedValue.equals(candidate) || hasSingleCharacterMismatch(normalizedExpectedValue, candidate)) {
                return true;
            }
        }

        return false;
    }

    private String simplifyIssuerName(String issuerName) {
        if (issuerName == null || issuerName.isBlank()) {
            return "";
        }

        return issuerName
                .replace("\uC6D0\uC7A5", "")
                .replace("\uD68C\uC7A5", "")
                .replace("\uC774\uC0AC\uC7A5", "")
                .replace("\uC7A5\uAD00", "")
                .trim();
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
