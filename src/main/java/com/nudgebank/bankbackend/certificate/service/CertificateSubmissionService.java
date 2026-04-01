package com.nudgebank.bankbackend.certificate.service;

import com.nudgebank.bankbackend.certificate.dto.CertificateSubmissionResponse;
import com.nudgebank.bankbackend.certificate.domain.CertificateSubmission;
import com.nudgebank.bankbackend.certificate.repository.CertificateSubmissionRepository;
import com.nudgebank.bankbackend.ocr.client.OcrClient;
import com.nudgebank.bankbackend.ocr.dto.OcrExtractResponse;
import com.nudgebank.bankbackend.ocr.exception.InvalidCertificateUploadException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;

@Service
public class CertificateSubmissionService {

    private static final String VERIFICATION_STATUS_COMPLETED = "OCR_COMPLETED";

    private final CertificateSubmissionRepository certificateSubmissionRepository;
    private final OcrClient ocrClient;

    public CertificateSubmissionService(
            CertificateSubmissionRepository certificateSubmissionRepository,
            OcrClient ocrClient
    ) {
        this.certificateSubmissionRepository = certificateSubmissionRepository;
        this.ocrClient = ocrClient;
    }

    @Transactional
    public CertificateSubmissionResponse submit(
            Long memberId,
            Long loanId,
            Long certificateId,
            MultipartFile file
    ) {
        validateRequest(memberId, loanId, certificateId, file);

        OcrExtractResponse ocrResponse = ocrClient.extract(file);
        OffsetDateTime submittedAt = OffsetDateTime.now();

        CertificateSubmission submission = CertificateSubmission.builder()
                .memberId(memberId)
                .loanId(loanId)
                .certificateId(certificateId)
                .fileUrl(file.getOriginalFilename())
                .ocrText(ocrResponse.extractedText())
                .verificationStatus(VERIFICATION_STATUS_COMPLETED)
                .submittedAt(submittedAt)
                .verifiedAt(submittedAt)
                .build();

        CertificateSubmission savedSubmission = certificateSubmissionRepository.save(submission);

        return new CertificateSubmissionResponse(
                savedSubmission.getSubmissionId(),
                ocrResponse.filename(),
                ocrResponse.contentType(),
                ocrResponse.extractedText(),
                ocrResponse.lines(),
                ocrResponse.lineCount(),
                savedSubmission.getVerificationStatus(),
                savedSubmission.getSubmittedAt()
        );
    }

    private void validateRequest(
            Long memberId,
            Long loanId,
            Long certificateId,
            MultipartFile file
    ) {
        if (memberId == null || loanId == null || certificateId == null) {
            throw new InvalidCertificateUploadException("memberId, loanId, certificateId are required");
        }

        if (file == null || file.isEmpty()) {
            throw new InvalidCertificateUploadException("Certificate file is required");
        }
    }
}
