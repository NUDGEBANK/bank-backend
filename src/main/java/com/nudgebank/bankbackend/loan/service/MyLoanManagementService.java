package com.nudgebank.bankbackend.loan.service;

import com.nudgebank.bankbackend.certificate.domain.CertificateMaster;
import com.nudgebank.bankbackend.certificate.domain.CertificateSubmission;
import com.nudgebank.bankbackend.certificate.repository.CertificateMasterRepository;
import com.nudgebank.bankbackend.certificate.repository.CertificateSubmissionRepository;
import com.nudgebank.bankbackend.loan.domain.Loan;
import com.nudgebank.bankbackend.loan.domain.LoanApplication;
import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import com.nudgebank.bankbackend.loan.domain.RepaymentSchedule;
import com.nudgebank.bankbackend.loan.dto.MyLoanRepaymentHistoryResponse;
import com.nudgebank.bankbackend.loan.dto.MyLoanRepaymentScheduleResponse;
import com.nudgebank.bankbackend.loan.dto.MyLoanSummaryResponse;
import com.nudgebank.bankbackend.loan.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyLoanManagementService {

    private static final String SELF_DEVELOPMENT_TYPE = "SELF_DEVELOPMENT";
    private static final String CONSUMPTION_ANALYSIS_TYPE = "CONSUMPTION_ANALYSIS";
    private static final String EMERGENCY_TYPE = "EMERGENCY";
    private static final String MATURITY_LUMP_SUM_TYPE = "MATURITY_LUMP_SUM";

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanHistoryRepository loanHistoryRepository;
    private final LoanRepository loanRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final LoanRepaymentHistoryRepository loanRepaymentHistoryRepository;
    private final CertificateSubmissionRepository certificateSubmissionRepository;
    private final CertificateMasterRepository certificateMasterRepository;

    public MyLoanSummaryResponse getSummary(Long memberId, String productKey) {
        LoanHistory loanHistory = resolveLoanHistory(memberId, productKey).orElse(null);
        if (loanHistory == null) {
            return buildPendingLoanSummary(memberId, productKey);
        }

        Loan loan = resolveLoan(memberId, productKey).orElse(null);
        List<RepaymentSchedule> schedules =
            repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());

        RepaymentSchedule nextSchedule = schedules.stream()
            .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()))
            .findFirst()
            .orElse(null);

        BigDecimal totalPrincipal = nullSafe(loanHistory.getTotalPrincipal());
        BigDecimal remainingPrincipal = nullSafe(loanHistory.getRemainingPrincipal());
        BigDecimal repaidPrincipal = totalPrincipal.subtract(remainingPrincipal);
        BigDecimal cumulativeInterest = schedules.stream()
            .map(RepaymentSchedule::getPaidInterest)
            .map(this::nullSafe)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingInterestAmount = schedules.stream()
            .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()))
            .map(schedule -> nullSafe(schedule.getPlannedInterest()).subtract(nullSafe(schedule.getPaidInterest())).max(BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        LoanApplication loanApplication = loan != null ? loan.getLoanApplication() : ensureDisplayableLoanExists(memberId, productKey);
        BigDecimal baseInterestRate = resolveBaseInterestRate(loanApplication);
        BigDecimal minimumInterestRate = resolveMinimumInterestRate(loanApplication);
        BigDecimal currentInterestRate = loan != null ? nullSafe(loan.getInterestRate()) : resolveInitialInterestRate(loanApplication);
        BigDecimal preferentialRateDiscount = resolvePreferentialRateDiscount(
            loanApplication.getId(),
            baseInterestRate,
            currentInterestRate
        );

        return new MyLoanSummaryResponse(
            loanHistory.getId(),
            loanHistory.getStatus(),
            totalPrincipal,
            remainingPrincipal,
            repaidPrincipal.max(BigDecimal.ZERO),
            baseInterestRate,
            minimumInterestRate,
            preferentialRateDiscount,
            currentInterestRate,
            loanApplication.getLoanProduct().getRepaymentType(),
            loanHistory.getStartDate(),
            loanHistory.getEndDate(),
            nextSchedule != null ? nextSchedule.getDueDate() : loanHistory.getExpectedRepaymentDate(),
            nextSchedule != null ? nullSafe(nextSchedule.getPlannedPrincipal()) : BigDecimal.ZERO,
            nextSchedule != null ? nullSafe(nextSchedule.getPlannedInterest()) : BigDecimal.ZERO,
            nextSchedule != null
                ? nullSafe(nextSchedule.getPlannedPrincipal()).add(nullSafe(nextSchedule.getPlannedInterest()))
                : BigDecimal.ZERO,
            cumulativeInterest,
            remainingInterestAmount,
            loanHistory.getRepaymentAccountNumber()
        );
    }

    public List<MyLoanRepaymentScheduleResponse> getRepaymentSchedules(Long memberId, String productKey) {
        LoanHistory loanHistory = resolveLoanHistory(memberId, productKey).orElse(null);
        if (loanHistory == null) {
            ensureDisplayableLoanExists(memberId, productKey);
            return List.of();
        }

        return repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId()).stream()
            .map(schedule -> new MyLoanRepaymentScheduleResponse(
                schedule.getScheduleId(),
                schedule.getDueDate(),
                nullSafe(schedule.getPlannedPrincipal()),
                nullSafe(schedule.getPlannedInterest()),
                nullSafe(schedule.getPaidPrincipal()),
                nullSafe(schedule.getPaidInterest()),
                Boolean.TRUE.equals(schedule.getIsSettled()),
                resolveOverdueDays(schedule)
            ))
            .toList();
    }

    public List<MyLoanRepaymentHistoryResponse> getRepaymentHistories(Long memberId, String productKey) {
        LoanHistory loanHistory = resolveLoanHistory(memberId, productKey).orElse(null);
        if (loanHistory == null) {
            ensureDisplayableLoanExists(memberId, productKey);
            return List.of();
        }

        return loanRepaymentHistoryRepository
            .findTop10ByLoanHistory_IdOrderByRepaymentDatetimeDesc(loanHistory.getId()).stream()
            .map(history -> new MyLoanRepaymentHistoryResponse(
                history.getRepaymentId(),
                nullSafe(history.getRepaymentAmount()),
                nullSafe(history.getRepaymentRate()),
                history.getRepaymentDatetime(),
                nullSafe(history.getRemainingBalance())
            ))
            .toList();
    }

    private MyLoanSummaryResponse buildPendingLoanSummary(Long memberId, String productKey) {
        LoanApplication application = ensureDisplayableLoanExists(memberId, productKey);
        BigDecimal totalPrincipal = nullSafe(application.getLoanAmount());
        BigDecimal interestRate = resolveInitialInterestRate(application);
        BigDecimal baseInterestRate = resolveBaseInterestRate(application);
        BigDecimal minimumInterestRate = resolveMinimumInterestRate(application);
        BigDecimal preferentialRateDiscount = resolvePreferentialRateDiscount(application.getId(), baseInterestRate, interestRate);
        int repaymentMonths = application.getLoanProduct().getRepaymentPeriodMonth() != null
            && application.getLoanProduct().getRepaymentPeriodMonth() > 0
            ? application.getLoanProduct().getRepaymentPeriodMonth()
            : 1;
        LocalDate startDate = application.getAppliedAt() != null
            ? application.getAppliedAt().toLocalDate()
            : LocalDate.now();
        BigDecimal nextPaymentAmount = resolvePendingNextPaymentAmount(
            application,
            totalPrincipal,
            interestRate,
            repaymentMonths
        );

        return new MyLoanSummaryResponse(
            null,
            application.getApplicationStatus().name(),
            totalPrincipal,
            totalPrincipal,
            BigDecimal.ZERO,
            baseInterestRate,
            minimumInterestRate,
            preferentialRateDiscount,
            interestRate,
            application.getLoanProduct().getRepaymentType(),
            startDate,
            startDate.plusMonths(repaymentMonths),
            startDate.plusMonths(1),
            resolvePendingNextPaymentPrincipal(application, totalPrincipal, repaymentMonths),
            resolvePendingNextPaymentInterest(application, totalPrincipal, interestRate),
            nextPaymentAmount,
            BigDecimal.ZERO,
            resolvePendingRemainingInterestAmount(application, totalPrincipal, interestRate, repaymentMonths),
            null
        );
    }

    private LoanApplication ensureDisplayableLoanExists(Long memberId, String productKey) {
        String loanProductType = toLoanProductType(productKey);
        return (loanProductType == null
            ? loanApplicationRepository.findTopByMember_MemberIdOrderByAppliedAtDesc(memberId)
            : loanApplicationRepository.findTopByMember_MemberIdAndLoanProduct_LoanProductTypeOrderByAppliedAtDesc(
                memberId,
                loanProductType
            ))
            .orElseThrow(() -> new EntityNotFoundException("대출 상품이 없습니다."));
    }

    private LoanHistory getLatestLoanHistory(Long memberId) {
        return loanHistoryRepository.findTopByMember_MemberIdOrderByCreatedAtDesc(memberId)
            .orElseThrow(() -> new EntityNotFoundException("대출 관리 정보가 없습니다."));
    }

    private java.util.Optional<Loan> resolveLoan(Long memberId, String productKey) {
        String loanProductType = toLoanProductType(productKey);
        if (loanProductType == null) {
            return loanRepository.findTopByMember_MemberIdOrderByStartDateDescIdDesc(memberId);
        }

        return loanRepository
            .findTopByMember_MemberIdAndLoanApplication_LoanProduct_LoanProductTypeOrderByStartDateDescIdDesc(
                memberId,
                loanProductType
            );
    }

    private java.util.Optional<LoanHistory> resolveLoanHistory(Long memberId, String productKey) {
        Loan loan = resolveLoan(memberId, productKey).orElse(null);
        if (loan != null && loan.getLoanApplication() != null && loan.getLoanApplication().getCard() != null) {
            return loanHistoryRepository
                .findTopByMember_MemberIdAndCard_CardIdAndTotalPrincipalAndStartDateAndEndDateOrderByCreatedAtDesc(
                    memberId,
                    loan.getLoanApplication().getCard().getCardId(),
                    nullSafe(loan.getPrincipalAmount()),
                    loan.getStartDate(),
                    loan.getEndDate()
                );
        }

        if (productKey == null || productKey.isBlank()) {
            return loanHistoryRepository.findTopByMember_MemberIdOrderByCreatedAtDesc(memberId);
        }

        return java.util.Optional.empty();
    }

    private BigDecimal resolveInitialInterestRate(LoanApplication application) {
        if (SELF_DEVELOPMENT_TYPE.equals(application.getLoanProduct().getLoanProductType())
            && application.getLoanProduct().getMaxInterestRate() != null) {
            return application.getLoanProduct().getMaxInterestRate();
        }

        return application.getLoanProduct().getMinInterestRate() != null
            ? application.getLoanProduct().getMinInterestRate()
            : BigDecimal.ZERO;
    }

    private BigDecimal resolveBaseInterestRate(LoanApplication application) {
        if (application.getLoanProduct().getMaxInterestRate() != null) {
            return application.getLoanProduct().getMaxInterestRate();
        }

        return resolveInitialInterestRate(application);
    }

    private BigDecimal resolveMinimumInterestRate(LoanApplication application) {
        return application.getLoanProduct().getMinInterestRate() != null
            ? application.getLoanProduct().getMinInterestRate()
            : BigDecimal.ZERO;
    }

    private BigDecimal resolvePreferentialRateDiscount(
        Long loanApplicationId,
        BigDecimal baseInterestRate,
        BigDecimal currentInterestRate
    ) {
        BigDecimal persistedDiscount = baseInterestRate.subtract(currentInterestRate).max(BigDecimal.ZERO);
        BigDecimal verifiedDiscount = certificateSubmissionRepository
            .findAllByLoanApplicationIdAndVerificationStatus(loanApplicationId, "VERIFIED")
            .stream()
            .map(CertificateSubmission::getCertificateId)
            .map(certificateId -> certificateMasterRepository.findByCertificateIdAndIsActiveTrue(certificateId)
                .map(CertificateMaster::getRateDiscount)
                .orElse(BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return persistedDiscount.max(verifiedDiscount);
    }

    private BigDecimal resolvePendingNextPaymentAmount(
        LoanApplication application,
        BigDecimal totalPrincipal,
        BigDecimal interestRate,
        int repaymentMonths
    ) {
        return resolvePendingNextPaymentPrincipal(application, totalPrincipal, repaymentMonths)
            .add(resolvePendingNextPaymentInterest(application, totalPrincipal, interestRate));
    }

    private BigDecimal resolvePendingNextPaymentPrincipal(
        LoanApplication application,
        BigDecimal totalPrincipal,
        int repaymentMonths
    ) {
        if (MATURITY_LUMP_SUM_TYPE.equals(application.getLoanProduct().getRepaymentType())) {
            return BigDecimal.ZERO;
        }

        return repaymentMonths > 0
            ? totalPrincipal.divide(BigDecimal.valueOf(repaymentMonths), 0, RoundingMode.UP)
            : totalPrincipal;
    }

    private BigDecimal resolvePendingNextPaymentInterest(
        LoanApplication application,
        BigDecimal totalPrincipal,
        BigDecimal interestRate
    ) {
        if (totalPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return totalPrincipal
            .multiply(interestRate)
            .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolvePendingRemainingInterestAmount(
        LoanApplication application,
        BigDecimal totalPrincipal,
        BigDecimal interestRate,
        int repaymentMonths
    ) {
        if (MATURITY_LUMP_SUM_TYPE.equals(application.getLoanProduct().getRepaymentType())) {
            return resolvePendingNextPaymentInterest(application, totalPrincipal, interestRate)
                .multiply(BigDecimal.valueOf(repaymentMonths));
        }

        BigDecimal remainingPrincipal = totalPrincipal;
        BigDecimal monthlyPrincipal = repaymentMonths > 0
            ? totalPrincipal.divide(BigDecimal.valueOf(repaymentMonths), 2, RoundingMode.DOWN)
            : totalPrincipal;
        BigDecimal allocatedPrincipal = BigDecimal.ZERO;
        BigDecimal remainingInterest = BigDecimal.ZERO;

        for (int month = 1; month <= repaymentMonths; month++) {
            BigDecimal plannedPrincipal = month == repaymentMonths
                ? totalPrincipal.subtract(allocatedPrincipal)
                : monthlyPrincipal;
            remainingInterest = remainingInterest.add(
                resolvePendingNextPaymentInterest(application, remainingPrincipal, interestRate)
            );
            allocatedPrincipal = allocatedPrincipal.add(plannedPrincipal);
            remainingPrincipal = remainingPrincipal.subtract(plannedPrincipal).max(BigDecimal.ZERO);
        }

        return remainingInterest;
    }

    private Integer resolveOverdueDays(RepaymentSchedule schedule) {
        if (Boolean.TRUE.equals(schedule.getIsSettled()) || schedule.getDueDate() == null) {
            return schedule.getOverdueDays();
        }

        long calculated = Math.max(0, ChronoUnit.DAYS.between(schedule.getDueDate(), LocalDate.now()));
        int existing = schedule.getOverdueDays() != null ? schedule.getOverdueDays() : 0;
        return Math.max(existing, (int) calculated);
    }

    private String toLoanProductType(String productKey) {
        if (productKey == null || productKey.isBlank()) {
            return null;
        }

        return switch (productKey) {
            case "youth-loan" -> SELF_DEVELOPMENT_TYPE;
            case "consumption-loan" -> CONSUMPTION_ANALYSIS_TYPE;
            case "situate-loan" -> EMERGENCY_TYPE;
            default -> throw new IllegalArgumentException("지원하지 않는 상품입니다. productKey=" + productKey);
        };
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
