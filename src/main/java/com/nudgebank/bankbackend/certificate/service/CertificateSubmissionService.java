package com.nudgebank.bankbackend.certificate.service;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.certificate.domain.CertificateSubmission;
import com.nudgebank.bankbackend.certificate.domain.CertificateVerificationStatus;
import com.nudgebank.bankbackend.certificate.dto.CertificateMatchResult;
import com.nudgebank.bankbackend.certificate.dto.CertificateSubmissionResponse;
import com.nudgebank.bankbackend.certificate.repository.CertificateSubmissionRepository;
import com.nudgebank.bankbackend.ocr.client.OcrClient;
import com.nudgebank.bankbackend.ocr.dto.OcrExtractResponse;
import com.nudgebank.bankbackend.ocr.exception.InvalidCertificateUploadException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class CertificateSubmissionService {

    private final MemberRepository memberRepository;
    private final CertificateSubmissionRepository certificateSubmissionRepository;
    private final OcrClient ocrClient;
    private final CertificateVerificationService certificateVerificationService;

    public CertificateSubmissionService(
            MemberRepository memberRepository,
            CertificateSubmissionRepository certificateSubmissionRepository,
            OcrClient ocrClient,
            CertificateVerificationService certificateVerificationService
    ) {
        this.memberRepository = memberRepository;
        this.certificateSubmissionRepository = certificateSubmissionRepository;
        this.ocrClient = ocrClient;
        this.certificateVerificationService = certificateVerificationService;
    }

    @Transactional
    public CertificateSubmissionResponse submit(
            Long memberId,
            Long loanId,
            Long certificateId,
            MultipartFile file
    ) {
        validateRequest(memberId, loanId, certificateId, file);
        validateDuplicateSubmission(memberId, certificateId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new InvalidCertificateUploadException("Member not found"));

        OcrExtractResponse ocrResponse = ocrClient.extract(file);
        OffsetDateTime submittedAt = OffsetDateTime.now();
        CertificateMatchResult matchResult = certificateVerificationService.verify(
                member,
                certificateId,
                ocrResponse.extractedText(),
                ocrResponse.lines()
        );

        CertificateSubmission submission = CertificateSubmission.builder()
                .memberId(memberId)
                .loanApplicationId(loanId)
                .certificateId(certificateId)
                .matchedIssuerName(matchResult.matchedIssuerName())
                .fileUrl(file.getOriginalFilename())
                .ocrText(ocrResponse.extractedText())
                .matchScore(defaultScore(matchResult.matchScore()))
                .detectedName(matchResult.detectedName())
                .nameMatchYn(matchResult.nameMatched())
                .verificationStatus(matchResult.verificationStatus().name())
                .reviewNote(matchResult.reviewNote())
                .submittedAt(submittedAt)
                .verifiedAt(matchResult.verifiedAt())
                .build();

        CertificateSubmission savedSubmission = certificateSubmissionRepository.save(submission);

        return new CertificateSubmissionResponse(
                savedSubmission.getSubmissionId(),
                ocrResponse.filename(),
                ocrResponse.contentType(),
                ocrResponse.extractedText(),
                ocrResponse.lines(),
                ocrResponse.lineCount(),
                matchResult.verificationStatus().name(),
                matchResult.failureReason(),
                savedSubmission.getSubmittedAt()
        );
    }

    private void validateDuplicateSubmission(Long memberId, Long certificateId) {
        if (certificateSubmissionRepository.existsByMemberIdAndCertificateIdAndVerificationStatus(
                memberId,
                certificateId,
                CertificateVerificationStatus.VERIFIED.name()
        )) {
            throw new InvalidCertificateUploadException("Certificate already verified for this member");
        }
    }

    private BigDecimal defaultScore(BigDecimal score) {
        return score == null ? BigDecimal.ZERO : score;
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
