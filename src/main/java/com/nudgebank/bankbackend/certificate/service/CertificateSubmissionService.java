package com.nudgebank.bankbackend.certificate.service;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.certificate.domain.CertificateSubmission;
import com.nudgebank.bankbackend.certificate.dto.CertificateMatchResult;
import com.nudgebank.bankbackend.certificate.dto.CertificateSubmissionResponse;
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
            Long loanApplicationId,
            Long certificateId,
            MultipartFile file
    ) {
        validateRequest(memberId, loanApplicationId, certificateId, file);
        validateDuplicateVerifiedCertificate(memberId, certificateId);

        OcrExtractResponse ocrResponse = ocrClient.extract(file);
        OffsetDateTime submittedAt = OffsetDateTime.now();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new InvalidCertificateUploadException("Member not found"));
        CertificateMatchResult matchResult = certificateVerificationService.verify(
                certificateId,
                ocrResponse.extractedText(),
                member
        );

        CertificateSubmission submission = CertificateSubmission.builder()
                .memberId(memberId)
                .loanApplicationId(loanApplicationId)
                .certificateId(certificateId)
                .fileUrl(file.getOriginalFilename())
                .ocrText(ocrResponse.extractedText())
                .verificationStatus(matchResult.verificationStatus().name())
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

    private void validateRequest(
            Long memberId,
            Long loanApplicationId,
            Long certificateId,
            MultipartFile file
    ) {
        if (memberId == null || loanApplicationId == null || certificateId == null) {
            throw new InvalidCertificateUploadException("memberId, loanApplicationId, certificateId are required");
        }

        if (file == null || file.isEmpty()) {
            throw new InvalidCertificateUploadException("Certificate file is required");
        }
    }

    private void validateDuplicateVerifiedCertificate(Long memberId, Long certificateId) {
        boolean alreadyVerified = certificateSubmissionRepository
                .existsByMemberIdAndCertificateIdAndVerificationStatus(
                        memberId,
                        certificateId,
                        "VERIFIED"
                );

        if (alreadyVerified) {
            throw new IllegalArgumentException("이미 인증 완료된 자격증입니다.");
        }
    }
}
