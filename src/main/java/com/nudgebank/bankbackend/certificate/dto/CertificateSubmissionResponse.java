package com.nudgebank.bankbackend.certificate.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record CertificateSubmissionResponse(
        Long submissionId,
        String filename,
        String contentType,
        String processingStatus,
        String detectedCertificateDate,
        String extractedText,
        List<String> lines,
        int lineCount,
        String verificationStatus,
        String failureReason,
        OffsetDateTime submittedAt
) {
}
