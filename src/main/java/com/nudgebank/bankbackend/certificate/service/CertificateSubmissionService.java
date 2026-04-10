package com.nudgebank.bankbackend.certificate.service;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.certificate.domain.CertificateMaster;
import com.nudgebank.bankbackend.certificate.domain.CertificateSubmission;
import com.nudgebank.bankbackend.certificate.dto.CertificateMatchResult;
import com.nudgebank.bankbackend.certificate.dto.CertificateSubmissionResponse;
import com.nudgebank.bankbackend.certificate.repository.CertificateMasterRepository;
import com.nudgebank.bankbackend.certificate.repository.CertificateSubmissionRepository;
import com.nudgebank.bankbackend.loan.domain.Loan;
import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import com.nudgebank.bankbackend.loan.domain.RepaymentSchedule;
import com.nudgebank.bankbackend.loan.repository.LoanHistoryRepository;
import com.nudgebank.bankbackend.loan.repository.LoanRepository;
import com.nudgebank.bankbackend.loan.repository.RepaymentScheduleRepository;
import com.nudgebank.bankbackend.ocr.client.OcrClient;
import com.nudgebank.bankbackend.ocr.dto.OcrExtractResponse;
import com.nudgebank.bankbackend.ocr.exception.InvalidCertificateUploadException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class CertificateSubmissionService {

    private final MemberRepository memberRepository;
    private final CertificateMasterRepository certificateMasterRepository;
    private final CertificateSubmissionRepository certificateSubmissionRepository;
    private final LoanRepository loanRepository;
    private final LoanHistoryRepository loanHistoryRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final OcrClient ocrClient;
    private final CertificateVerificationService certificateVerificationService;

    public CertificateSubmissionService(
            MemberRepository memberRepository,
            CertificateMasterRepository certificateMasterRepository,
            CertificateSubmissionRepository certificateSubmissionRepository,
            LoanRepository loanRepository,
            LoanHistoryRepository loanHistoryRepository,
            RepaymentScheduleRepository repaymentScheduleRepository,
            OcrClient ocrClient,
            CertificateVerificationService certificateVerificationService
    ) {
        this.memberRepository = memberRepository;
        this.certificateMasterRepository = certificateMasterRepository;
        this.certificateSubmissionRepository = certificateSubmissionRepository;
        this.loanRepository = loanRepository;
        this.loanHistoryRepository = loanHistoryRepository;
        this.repaymentScheduleRepository = repaymentScheduleRepository;
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
        if ("VERIFIED".equals(matchResult.verificationStatus().name())) {
            applyPreferentialRate(loanApplicationId);
        }

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

    private void applyPreferentialRate(Long loanApplicationId) {
        Loan loan = loanRepository.findTopByLoanApplication_IdOrderByIdDesc(loanApplicationId)
                .orElseThrow(() -> new InvalidCertificateUploadException("대출 정보를 찾을 수 없습니다."));

        LoanHistory loanHistory = loanHistoryRepository
                .findTopByMember_MemberIdAndCard_CardIdAndTotalPrincipalAndStartDateAndEndDateOrderByCreatedAtDesc(
                        loan.getMember().getMemberId(),
                        loan.getLoanApplication().getCard().getCardId(),
                        nullSafe(loan.getPrincipalAmount()),
                        loan.getStartDate(),
                        loan.getEndDate()
                )
                .orElseThrow(() -> new InvalidCertificateUploadException("대출 이력을 찾을 수 없습니다."));

        BigDecimal totalDiscount = certificateSubmissionRepository
                .findAllByLoanApplicationIdAndVerificationStatus(loanApplicationId, "VERIFIED")
                .stream()
                .map(CertificateSubmission::getCertificateId)
                .map(certificateId -> certificateMasterRepository.findByCertificateIdAndIsActiveTrue(certificateId)
                        .map(CertificateMaster::getRateDiscount)
                        .orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal baseRate = loan.getLoanApplication().getLoanProduct().getMaxInterestRate() != null
                ? loan.getLoanApplication().getLoanProduct().getMaxInterestRate()
                : nullSafe(loan.getInterestRate());
        BigDecimal minimumRate = loan.getLoanApplication().getLoanProduct().getMinInterestRate() != null
                ? loan.getLoanApplication().getLoanProduct().getMinInterestRate()
                : BigDecimal.ZERO;
        BigDecimal nextRate = baseRate.subtract(totalDiscount).max(minimumRate);

        loan.updateInterestRate(nextRate);
        refreshPendingScheduleInterest(loanHistory, nextRate);
    }

    private void refreshPendingScheduleInterest(LoanHistory loanHistory, BigDecimal annualInterestRate) {
        List<RepaymentSchedule> schedules =
                repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());

        BigDecimal remainingPrincipal = nullSafe(loanHistory.getRemainingPrincipal());
        for (RepaymentSchedule schedule : schedules) {
            if (Boolean.TRUE.equals(schedule.getIsSettled())) {
                remainingPrincipal = remainingPrincipal.subtract(nullSafe(schedule.getPlannedPrincipal())).max(BigDecimal.ZERO);
                continue;
            }

            BigDecimal plannedInterest = remainingPrincipal
                    .multiply(annualInterestRate)
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

            schedule.updatePlannedInterest(plannedInterest);
            remainingPrincipal = remainingPrincipal.subtract(nullSafe(schedule.getPlannedPrincipal())).max(BigDecimal.ZERO);
        }
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
