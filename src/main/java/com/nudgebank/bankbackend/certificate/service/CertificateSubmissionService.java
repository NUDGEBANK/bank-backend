package com.nudgebank.bankbackend.certificate.service;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.certificate.domain.CertificateMaster;
import com.nudgebank.bankbackend.certificate.domain.CertificateSubmission;
import com.nudgebank.bankbackend.certificate.domain.CertificateVerificationStatus;
import com.nudgebank.bankbackend.certificate.dto.CertificateMatchResult;
import com.nudgebank.bankbackend.certificate.dto.CertificateSubmissionResponse;
import com.nudgebank.bankbackend.certificate.repository.CertificateMasterRepository;
import com.nudgebank.bankbackend.certificate.repository.CertificateSubmissionRepository;
import com.nudgebank.bankbackend.loan.domain.Loan;
import com.nudgebank.bankbackend.loan.domain.LoanApplication;
import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import com.nudgebank.bankbackend.loan.domain.LoanProduct;
import com.nudgebank.bankbackend.loan.domain.RepaymentSchedule;
import com.nudgebank.bankbackend.loan.repository.LoanApplicationRepository;
import com.nudgebank.bankbackend.loan.repository.LoanHistoryRepository;
import com.nudgebank.bankbackend.loan.repository.LoanRepository;
import com.nudgebank.bankbackend.loan.repository.RepaymentScheduleRepository;
import com.nudgebank.bankbackend.ocr.client.OcrClient;
import com.nudgebank.bankbackend.ocr.dto.OcrExtractResponse;
import com.nudgebank.bankbackend.ocr.exception.InvalidCertificateUploadException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CertificateSubmissionService {
    private static final String SELF_DEVELOPMENT_TYPE = "SELF_DEVELOPMENT";

    private final MemberRepository memberRepository;
    private final CertificateMasterRepository certificateMasterRepository;
    private final CertificateSubmissionRepository certificateSubmissionRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanRepository loanRepository;
    private final LoanHistoryRepository loanHistoryRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final OcrClient ocrClient;
    private final CertificateVerificationService certificateVerificationService;

    public CertificateSubmissionService(
            MemberRepository memberRepository,
            CertificateMasterRepository certificateMasterRepository,
            CertificateSubmissionRepository certificateSubmissionRepository,
            LoanApplicationRepository loanApplicationRepository,
            LoanRepository loanRepository,
            LoanHistoryRepository loanHistoryRepository,
            RepaymentScheduleRepository repaymentScheduleRepository,
            OcrClient ocrClient,
            CertificateVerificationService certificateVerificationService
    ) {
        this.memberRepository = memberRepository;
        this.certificateMasterRepository = certificateMasterRepository;
        this.certificateSubmissionRepository = certificateSubmissionRepository;
        this.loanApplicationRepository = loanApplicationRepository;
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

        LoanApplication loanApplication = loanApplicationRepository.findByIdAndMember_MemberId(loanApplicationId, memberId)
                .orElseThrow(() -> new InvalidCertificateUploadException("\uB300\uCD9C \uC2E0\uCCAD \uC815\uBCF4\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."));

        OcrExtractResponse ocrResponse = ocrClient.extract(file);
        OffsetDateTime submittedAt = OffsetDateTime.now();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new InvalidCertificateUploadException("\uD68C\uC6D0 \uC815\uBCF4\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."));

        CertificateMatchResult matchResult = certificateVerificationService.verify(
                certificateId,
                ocrResponse.extractedText(),
                member
        );
        LocalDate certificateDate = certificateVerificationService.extractCertificateDate(ocrResponse.extractedText());
        matchResult = applyCertificateDateValidation(matchResult, loanApplication, certificateDate);

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
        if (CertificateVerificationStatus.VERIFIED.equals(matchResult.verificationStatus())) {
            applyPreferentialRate(loanApplicationId);
        }

        return new CertificateSubmissionResponse(
                savedSubmission.getSubmissionId(),
                ocrResponse.filename(),
                ocrResponse.contentType(),
                "VERIFICATION_COMPLETED",
                certificateDate != null ? certificateDate.toString() : null,
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
            throw new InvalidCertificateUploadException("memberId, loanApplicationId, certificateId\uB294 \uD544\uC218\uC785\uB2C8\uB2E4.");
        }

        if (file == null || file.isEmpty()) {
            throw new InvalidCertificateUploadException("\uC790\uACA9\uC99D \uD30C\uC77C\uC744 \uCCA8\uBD80\uD574 \uC8FC\uC138\uC694.");
        }

        LoanApplication loanApplication = loanApplicationRepository.findByIdAndMember_MemberId(loanApplicationId, memberId)
                .orElseThrow(() -> new InvalidCertificateUploadException("\uB300\uCD9C \uC2E0\uCCAD \uC815\uBCF4\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."));
        if (!SELF_DEVELOPMENT_TYPE.equals(loanApplication.getLoanProduct().getLoanProductType())) {
            throw new InvalidCertificateUploadException("\uC790\uAE30\uACC4\uBC1C \uB300\uCD9C \uC2E0\uCCAD \uAC74\uC5D0\uC11C\uB9CC \uC790\uACA9\uC99D \uC778\uC99D\uC774 \uAC00\uB2A5\uD569\uB2C8\uB2E4.");
        }
    }

    private CertificateMatchResult applyCertificateDateValidation(
            CertificateMatchResult matchResult,
            LoanApplication loanApplication,
            LocalDate certificateDate
    ) {
        if (!CertificateVerificationStatus.VERIFIED.equals(matchResult.verificationStatus())) {
            return matchResult;
        }

        if (loanApplication.getAppliedAt() == null) {
            return new CertificateMatchResult(
                    CertificateVerificationStatus.VERIFICATION_FAILED,
                    null,
                    "LOAN_APPLICATION_DATE_NOT_FOUND"
            );
        }

        if (certificateDate == null) {
            return new CertificateMatchResult(
                    CertificateVerificationStatus.VERIFICATION_FAILED,
                    null,
                    "CERTIFICATE_DATE_NOT_FOUND"
            );
        }

        LocalDate applicationDate = loanApplication.getAppliedAt().toLocalDate();
        if (certificateDate.isBefore(applicationDate)) {
            return new CertificateMatchResult(
                    CertificateVerificationStatus.VERIFICATION_FAILED,
                    null,
                    "CERTIFICATE_DATE_BEFORE_APPLICATION"
            );
        }

        return matchResult;
    }

    private void validateDuplicateVerifiedCertificate(Long memberId, Long certificateId) {
        boolean alreadyVerified = certificateSubmissionRepository
                .existsByMemberIdAndCertificateIdAndVerificationStatus(
                        memberId,
                        certificateId,
                        "VERIFIED"
                );

        if (alreadyVerified) {
            throw new IllegalArgumentException("\uC774\uBBF8 \uC778\uC99D \uC644\uB8CC\uB41C \uC790\uACA9\uC99D\uC785\uB2C8\uB2E4.");
        }
    }

    private void applyPreferentialRate(Long loanApplicationId) {
        LoanApplication loanApplication = loanApplicationRepository.findById(loanApplicationId)
                .orElseThrow(() -> new InvalidCertificateUploadException("\uB300\uCD9C \uC2E0\uCCAD \uC815\uBCF4\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."));
        Loan loan = loanRepository.findTopByLoanApplication_IdOrderByIdDesc(loanApplicationId)
                .orElseThrow(() -> new InvalidCertificateUploadException("\uB300\uCD9C \uC815\uBCF4\uB97C \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."));

        LoanHistory loanHistory = loanHistoryRepository
                .findTopByMember_MemberIdAndCard_CardIdAndTotalPrincipalAndStartDateAndEndDateOrderByCreatedAtDesc(
                        loan.getMember().getMemberId(),
                        loan.getLoanApplication().getCard().getCardId(),
                        nullSafe(loan.getPrincipalAmount()),
                        loan.getStartDate(),
                        loan.getEndDate()
                )
                .orElseThrow(() -> new InvalidCertificateUploadException("\uB300\uCD9C \uC774\uB825\uC744 \uCC3E\uC744 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4."));

        BigDecimal totalDiscount = certificateSubmissionRepository
                .findAllByLoanApplicationIdAndVerificationStatus(loanApplicationId, "VERIFIED")
                .stream()
                .map(CertificateSubmission::getCertificateId)
                .map(id -> certificateMasterRepository.findByCertificateIdAndIsActiveTrue(id)
                        .map(CertificateMaster::getRateDiscount)
                        .orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal baseRate = requireInterestRate(loanApplication.getLoanProduct(), true);
        BigDecimal minimumRate = requireInterestRate(loanApplication.getLoanProduct(), false);
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
                    .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
                    .setScale(0, RoundingMode.DOWN);

            schedule.updatePlannedInterest(plannedInterest);
            remainingPrincipal = remainingPrincipal.subtract(nullSafe(schedule.getPlannedPrincipal())).max(BigDecimal.ZERO);
        }
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal requireInterestRate(LoanProduct loanProduct, boolean baseRate) {
        BigDecimal rate = baseRate ? loanProduct.getMaxInterestRate() : loanProduct.getMinInterestRate();
        if (rate == null) {
            throw new IllegalStateException(
                    baseRate
                            ? "\uAE30\uC900 \uAE08\uB9AC\uAC00 \uC124\uC815\uB418\uC5B4 \uC788\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4."
                            : "\uCD5C\uC800 \uAE08\uB9AC\uAC00 \uC124\uC815\uB418\uC5B4 \uC788\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4."
            );
        }
        return rate;
    }
}
