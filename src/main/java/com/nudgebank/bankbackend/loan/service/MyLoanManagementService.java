package com.nudgebank.bankbackend.loan.service;

import com.nudgebank.bankbackend.certificate.domain.CertificateMaster;
import com.nudgebank.bankbackend.certificate.domain.CertificateSubmission;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.certificate.repository.CertificateMasterRepository;
import com.nudgebank.bankbackend.certificate.repository.CertificateSubmissionRepository;
import com.nudgebank.bankbackend.common.util.WonAmount;
import com.nudgebank.bankbackend.loan.dto.CompletedLoanHistoryResponse;
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
import org.springframework.data.domain.PageRequest;
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
    private static final String MATURITY_LUMP_SUM_TYPE = "MATURITY_LUMP_SUM";
    private static final String EQUAL_INSTALLMENT_TYPE = "EQUAL_INSTALLMENT";

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanHistoryRepository loanHistoryRepository;
    private final LoanRepository loanRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final LoanRepaymentHistoryRepository loanRepaymentHistoryRepository;
    private final CertificateSubmissionRepository certificateSubmissionRepository;
    private final CertificateMasterRepository certificateMasterRepository;

    @Transactional
    public MyLoanSummaryResponse getSummary(Long memberId, String productKey) {
        LoanHistory loanHistory = resolveLoanHistory(memberId, productKey).orElse(null);
        if (loanHistory == null) {
            return buildPendingLoanSummary(memberId, productKey);
        }

        Loan loan = resolveLoan(memberId, productKey).orElse(null);
        List<RepaymentSchedule> schedules =
            repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());
        if (loan != null) {
            refreshEqualInstallmentSchedulesIfNeeded(loanHistory, loan, schedules);
        }

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
        LocalDate displayEndDate = resolveDisplayEndDate(loanApplication, loanHistory, schedules);

        return new MyLoanSummaryResponse(
            loanHistory.getId(),
            loanHistory.getStatus(),
            won(totalPrincipal),
            won(remainingPrincipal),
            won(repaidPrincipal.max(BigDecimal.ZERO)),
            baseInterestRate,
            minimumInterestRate,
            preferentialRateDiscount,
            currentInterestRate,
            loanApplication.getLoanProduct().getRepaymentType(),
            loanHistory.getStartDate(),
            displayEndDate,
            nextSchedule != null ? nextSchedule.getDueDate() : loanHistory.getExpectedRepaymentDate(),
            nextSchedule != null ? won(nextSchedule.getPlannedPrincipal()) : BigDecimal.ZERO,
            nextSchedule != null ? won(nextSchedule.getPlannedInterest()) : BigDecimal.ZERO,
            nextSchedule != null
                ? won(nullSafe(nextSchedule.getPlannedPrincipal()).add(nullSafe(nextSchedule.getPlannedInterest())))
                : BigDecimal.ZERO,
            won(cumulativeInterest),
            won(remainingInterestAmount),
            loanHistory.getRepaymentAccountNumber()
        );
    }

    @Transactional
    public List<MyLoanRepaymentScheduleResponse> getRepaymentSchedules(Long memberId, String productKey) {
        LoanHistory loanHistory = resolveLoanHistory(memberId, productKey).orElse(null);
        if (loanHistory == null) {
            ensureDisplayableLoanExists(memberId, productKey);
            return List.of();
        }

        Loan loan = resolveLoan(memberId, productKey).orElse(null);
        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());
        if (loan != null) {
            refreshEqualInstallmentSchedulesIfNeeded(loanHistory, loan, schedules);
        }

        return schedules.stream()
            .map(schedule -> new MyLoanRepaymentScheduleResponse(
                schedule.getScheduleId(),
                schedule.getDueDate(),
                won(schedule.getPlannedPrincipal()),
                won(schedule.getPlannedInterest()),
                won(schedule.getPaidPrincipal()),
                won(schedule.getPaidInterest()),
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
            .findByLoanHistoryIdWithTransaction(loanHistory.getId(), PageRequest.of(0, 10)).stream()
            .map(history -> new MyLoanRepaymentHistoryResponse(
                history.getRepaymentId(),
                won(history.getRepaymentAmount()),
                nullSafe(history.getRepaymentRate()),
                history.getRepaymentDatetime(),
                won(history.getRemainingBalance()),
                history.getPolicyReason(),
                toTransactionInfo(history.getTransaction())
            ))
            .toList();
    }

    private MyLoanRepaymentHistoryResponse.TransactionInfo toTransactionInfo(CardTransaction transaction) {
        if (transaction == null) {
            return null;
        }

        return new MyLoanRepaymentHistoryResponse.TransactionInfo(
            transaction.getTransactionId(),
            transaction.getCard() != null ? transaction.getCard().getCardId() : null,
            transaction.getMarket() != null ? transaction.getMarket().getMarketId() : null,
            transaction.getCategory() != null ? transaction.getCategory().getCategoryId() : null,
            transaction.getQrId(),
            won(transaction.getAmount()),
            transaction.getTransactionDatetime(),
            transaction.getMenuName(),
            transaction.getQuantity()
        );
    }

    public List<CompletedLoanHistoryResponse> getCompletedLoans(Long memberId) {
        return loanHistoryRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId).stream()
            .filter(loanHistory -> "COMPLETED".equals(loanHistory.getStatus()))
            .map(loanHistory -> {
                Loan loan = resolveLoanForHistory(memberId, loanHistory)
                    .orElseThrow(() -> new EntityNotFoundException("완납 대출 정보를 찾을 수 없습니다."));
                LoanApplication application = loan.getLoanApplication();
                return new CompletedLoanHistoryResponse(
                    loanHistory.getId(),
                    toProductKey(application.getLoanProduct().getLoanProductType()),
                    application.getLoanProduct().getLoanProductName(),
                    loanHistory.getStatus(),
                    won(loanHistory.getTotalPrincipal()),
                    nullSafe(loan.getInterestRate()),
                    application.getLoanProduct().getRepaymentType(),
                    loanHistory.getStartDate(),
                    loanHistory.getEndDate()
                );
            })
            .toList();
    }

    public MyLoanSummaryResponse getCompletedLoanSummary(Long memberId, Long loanHistoryId) {
        LoanHistory loanHistory = loanHistoryRepository.findById(loanHistoryId)
            .filter(history -> history.getMember().getMemberId().equals(memberId))
            .filter(history -> "COMPLETED".equals(history.getStatus()))
            .orElseThrow(() -> new EntityNotFoundException("완납 대출 이력을 찾을 수 없습니다."));

        Loan loan = resolveLoanForHistory(memberId, loanHistory)
            .orElseThrow(() -> new EntityNotFoundException("완납 대출 정보를 찾을 수 없습니다."));
        LoanApplication application = loan.getLoanApplication();
        List<RepaymentSchedule> schedules =
            repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId());

        BigDecimal totalPrincipal = nullSafe(loanHistory.getTotalPrincipal());
        BigDecimal remainingPrincipal = nullSafe(loanHistory.getRemainingPrincipal());
        BigDecimal repaidPrincipal = totalPrincipal.subtract(remainingPrincipal);
        BigDecimal cumulativeInterest = schedules.stream()
            .map(RepaymentSchedule::getPaidInterest)
            .map(this::nullSafe)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal baseInterestRate = resolveBaseInterestRate(application);
        BigDecimal minimumInterestRate = resolveMinimumInterestRate(application);
        BigDecimal currentInterestRate = nullSafe(loan.getInterestRate());
        BigDecimal preferentialRateDiscount = resolvePreferentialRateDiscount(
            application.getId(),
            baseInterestRate,
            currentInterestRate
        );

        return new MyLoanSummaryResponse(
            loanHistory.getId(),
            loanHistory.getStatus(),
            won(totalPrincipal),
            won(remainingPrincipal),
            won(repaidPrincipal.max(BigDecimal.ZERO)),
            baseInterestRate,
            minimumInterestRate,
            preferentialRateDiscount,
            currentInterestRate,
            application.getLoanProduct().getRepaymentType(),
            loanHistory.getStartDate(),
            loanHistory.getEndDate(),
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            won(cumulativeInterest),
            BigDecimal.ZERO,
            loanHistory.getRepaymentAccountNumber()
        );
    }

    public List<MyLoanRepaymentScheduleResponse> getCompletedRepaymentSchedules(Long memberId, Long loanHistoryId) {
        LoanHistory loanHistory = loanHistoryRepository.findById(loanHistoryId)
            .filter(history -> history.getMember().getMemberId().equals(memberId))
            .filter(history -> "COMPLETED".equals(history.getStatus()))
            .orElseThrow(() -> new EntityNotFoundException("완납 대출 이력을 찾을 수 없습니다."));

        return repaymentScheduleRepository.findAllByLoanHistory_IdOrderByDueDateAsc(loanHistory.getId()).stream()
            .map(schedule -> new MyLoanRepaymentScheduleResponse(
                schedule.getScheduleId(),
                schedule.getDueDate(),
                won(schedule.getPlannedPrincipal()),
                won(schedule.getPlannedInterest()),
                won(schedule.getPaidPrincipal()),
                won(schedule.getPaidInterest()),
                Boolean.TRUE.equals(schedule.getIsSettled()),
                resolveOverdueDays(schedule)
            ))
            .toList();
    }

    public List<MyLoanRepaymentHistoryResponse> getCompletedRepaymentHistories(Long memberId, Long loanHistoryId) {
        LoanHistory loanHistory = loanHistoryRepository.findById(loanHistoryId)
            .filter(history -> history.getMember().getMemberId().equals(memberId))
            .filter(history -> "COMPLETED".equals(history.getStatus()))
            .orElseThrow(() -> new EntityNotFoundException("완납 대출 이력을 찾을 수 없습니다."));

        return loanRepaymentHistoryRepository
            .findTop10ByLoanHistory_IdOrderByRepaymentDatetimeDesc(loanHistory.getId()).stream()
            .map(history -> new MyLoanRepaymentHistoryResponse(
                history.getRepaymentId(),
                won(history.getRepaymentAmount()),
                nullSafe(history.getRepaymentRate()),
                history.getRepaymentDatetime(),
                won(history.getRemainingBalance()),
                history.getPolicyReason(),
                toTransactionInfo(history.getTransaction())
            ))
            .toList();
    }

    private MyLoanSummaryResponse buildPendingLoanSummary(Long memberId, String productKey) {
        LoanApplication application = ensureDisplayableLoanExists(memberId, productKey);
        BigDecimal totalPrincipal = won(application.getLoanAmount());
        BigDecimal interestRate = resolveInitialInterestRate(application);
        BigDecimal baseInterestRate = resolveBaseInterestRate(application);
        BigDecimal minimumInterestRate = resolveMinimumInterestRate(application);
        BigDecimal preferentialRateDiscount = resolvePreferentialRateDiscount(application.getId(), baseInterestRate, interestRate);
        int repaymentMonths = resolveRepaymentMonths(application);
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
            won(totalPrincipal),
            won(totalPrincipal),
            BigDecimal.ZERO,
            baseInterestRate,
            minimumInterestRate,
            preferentialRateDiscount,
            interestRate,
            application.getLoanProduct().getRepaymentType(),
            startDate,
            startDate.plusMonths(repaymentMonths),
            startDate.plusMonths(1),
            won(resolvePendingNextPaymentPrincipal(application, totalPrincipal, interestRate, repaymentMonths)),
            won(resolvePendingNextPaymentInterest(application, totalPrincipal, interestRate)),
            won(nextPaymentAmount),
            BigDecimal.ZERO,
            won(resolvePendingRemainingInterestAmount(application, totalPrincipal, interestRate, repaymentMonths)),
            null
        );
    }

    private int resolveRepaymentMonths(LoanApplication application) {
        String loanTerm = application.getLoanTerm();
        if (loanTerm != null) {
            String digits = loanTerm.replaceAll("[^0-9]", "");
            if (!digits.isBlank()) {
                return Integer.parseInt(digits);
            }
        }

        Integer repaymentPeriodMonth = application.getLoanProduct().getRepaymentPeriodMonth();
        if (repaymentPeriodMonth != null && repaymentPeriodMonth > 0) {
            return repaymentPeriodMonth;
        }

        return 12;
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
            java.util.Optional<LoanHistory> matchedLoanHistory = loanHistoryRepository
                .findTopByMember_MemberIdAndCard_CardIdAndTotalPrincipalAndStartDateOrderByCreatedAtDesc(
                    memberId,
                    loan.getLoanApplication().getCard().getCardId(),
                    nullSafe(loan.getPrincipalAmount()),
                    loan.getStartDate()
                );
            if (matchedLoanHistory.isPresent()) {
                return matchedLoanHistory;
            }

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

    private java.util.Optional<Loan> resolveLoanForHistory(Long memberId, LoanHistory loanHistory) {
        BigDecimal normalizedPrincipalAmount = WonAmount.floor(loanHistory.getTotalPrincipal());

        java.util.Optional<Loan> matchedLoan = loanRepository
            .findTopByMember_MemberIdAndLoanApplication_Card_CardIdAndPrincipalAmountAndStartDateOrderByIdDesc(
                memberId,
                loanHistory.getCard().getCardId(),
                normalizedPrincipalAmount,
                loanHistory.getStartDate()
            );
        if (matchedLoan.isPresent()) {
            return matchedLoan;
        }

        return loanRepository
            .findTopByMember_MemberIdAndLoanApplication_Card_CardIdAndPrincipalAmountAndStartDateAndEndDateOrderByIdDesc(
                memberId,
                loanHistory.getCard().getCardId(),
                normalizedPrincipalAmount,
                loanHistory.getStartDate(),
                loanHistory.getEndDate()
            );
    }

    private BigDecimal resolveInitialInterestRate(LoanApplication application) {
        if (SELF_DEVELOPMENT_TYPE.equals(application.getLoanProduct().getLoanProductType())
            && application.getLoanProduct().getMaxInterestRate() != null) {
            return application.getLoanProduct().getMaxInterestRate();
        }
        if (CONSUMPTION_ANALYSIS_TYPE.equals(application.getLoanProduct().getLoanProductType())) {
            int creditScore = application.getCreditHistory() != null && application.getCreditHistory().getCreditScore() != null
                ? application.getCreditHistory().getCreditScore()
                : ConsumptionAnalysisInterestPolicy.MIN_ELIGIBLE_CREDIT_SCORE;
            return ConsumptionAnalysisInterestPolicy.resolve(creditScore);
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
        return resolvePendingNextPaymentPrincipal(application, totalPrincipal, interestRate, repaymentMonths)
            .add(resolvePendingNextPaymentInterest(application, totalPrincipal, interestRate));
    }

    private BigDecimal resolvePendingNextPaymentPrincipal(
        LoanApplication application,
        BigDecimal totalPrincipal,
        BigDecimal interestRate,
        int repaymentMonths
    ) {
        String repaymentType = application.getLoanProduct().getRepaymentType();
        if (MATURITY_LUMP_SUM_TYPE.equals(repaymentType)) {
            return BigDecimal.ZERO;
        }
        if (isConsumptionEqualInstallment(application)) {
            return calculateEqualInstallmentAmount(totalPrincipal, interestRate, repaymentMonths)
                .subtract(resolvePendingNextPaymentInterest(application, totalPrincipal, interestRate))
                .max(BigDecimal.ZERO);
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
            .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
            .setScale(0, RoundingMode.DOWN);
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
        if (isConsumptionEqualInstallment(application)) {
            return calculateEqualInstallmentTotalInterest(totalPrincipal, interestRate, repaymentMonths);
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

    private BigDecimal calculateEqualInstallmentAmount(
        BigDecimal principalAmount,
        BigDecimal annualInterestRate,
        int repaymentMonths
    ) {
        if (repaymentMonths <= 0) {
            return principalAmount;
        }
        if (principalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal monthlyRate = annualInterestRate
            .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principalAmount.divide(BigDecimal.valueOf(repaymentMonths), 0, RoundingMode.DOWN);
        }

        BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(repaymentMonths);
        BigDecimal numerator = principalAmount.multiply(monthlyRate).multiply(factor);
        BigDecimal denominator = factor.subtract(BigDecimal.ONE);

        return numerator
            .divide(denominator, 10, RoundingMode.HALF_UP)
            .setScale(0, RoundingMode.DOWN);
    }

    private BigDecimal calculateEqualInstallmentTotalInterest(
        BigDecimal principalAmount,
        BigDecimal annualInterestRate,
        int repaymentMonths
    ) {
        if (repaymentMonths <= 0 || principalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal monthlyPayment = calculateEqualInstallmentAmount(principalAmount, annualInterestRate, repaymentMonths);
        BigDecimal remainingPrincipal = principalAmount;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal allocatedPrincipal = BigDecimal.ZERO;

        for (int month = 1; month <= repaymentMonths; month++) {
            BigDecimal plannedInterest = remainingPrincipal
                .multiply(annualInterestRate)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.DOWN);
            BigDecimal plannedPrincipal = month == repaymentMonths
                ? principalAmount.subtract(allocatedPrincipal)
                : monthlyPayment.subtract(plannedInterest).max(BigDecimal.ZERO);

            totalInterest = totalInterest.add(plannedInterest);
            allocatedPrincipal = allocatedPrincipal.add(plannedPrincipal);
            remainingPrincipal = remainingPrincipal.subtract(plannedPrincipal).max(BigDecimal.ZERO);
        }

        return totalInterest;
    }

    private void refreshEqualInstallmentSchedulesIfNeeded(
        LoanHistory loanHistory,
        Loan loan,
        List<RepaymentSchedule> schedules
    ) {
        if (loan.getLoanApplication() == null
            || loan.getLoanApplication().getLoanProduct() == null
            || !CONSUMPTION_ANALYSIS_TYPE.equals(loan.getLoanApplication().getLoanProduct().getLoanProductType())
            || !EQUAL_INSTALLMENT_TYPE.equals(loan.getLoanApplication().getLoanProduct().getRepaymentType())
            || schedules.isEmpty()) {
            return;
        }

        List<RepaymentSchedule> unsettledSchedules = schedules.stream()
            .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()))
            .toList();
        if (unsettledSchedules.isEmpty()) {
            return;
        }

        BigDecimal totalPrincipal = nullSafe(loanHistory.getTotalPrincipal());
        BigDecimal annualInterestRate = nullSafe(loan.getInterestRate());
        BigDecimal monthlyPayment = calculateEqualInstallmentAmount(totalPrincipal, annualInterestRate, schedules.size());
        BigDecimal remainingPrincipal = nullSafe(loanHistory.getRemainingPrincipal());
        boolean changed = false;

        int remainingUntouchedSchedules = (int) unsettledSchedules.stream()
            .filter(schedule -> !hasRecordedPayment(schedule))
            .count();

        for (RepaymentSchedule schedule : unsettledSchedules) {
            if (hasRecordedPayment(schedule)) {
                if (schedule.normalizePaidAmounts()) {
                    changed = true;
                }
                remainingPrincipal = remainingPrincipal.subtract(nullSafe(schedule.getRemainingPlannedPrincipal())).max(BigDecimal.ZERO);
                continue;
            }

            BigDecimal plannedInterest = remainingPrincipal
                .multiply(annualInterestRate)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.DOWN);
            BigDecimal plannedPrincipal = remainingUntouchedSchedules == 1
                ? remainingPrincipal
                : monthlyPayment.subtract(plannedInterest).max(BigDecimal.ZERO).min(remainingPrincipal);

            plannedPrincipal = floorWon(plannedPrincipal);
            plannedInterest = floorWon(plannedInterest);

            if (nullSafe(schedule.getPlannedPrincipal()).compareTo(plannedPrincipal) != 0
                || nullSafe(schedule.getPlannedInterest()).compareTo(plannedInterest) != 0) {
                schedule.updatePlannedAmounts(plannedPrincipal, plannedInterest);
                changed = true;
            }

            remainingPrincipal = remainingPrincipal.subtract(plannedPrincipal).max(BigDecimal.ZERO);
            remainingUntouchedSchedules--;
        }

        if (changed) {
            repaymentScheduleRepository.saveAll(schedules);
        }
    }

    private LocalDate resolveDisplayEndDate(
        LoanApplication application,
        LoanHistory loanHistory,
        List<RepaymentSchedule> schedules
    ) {
        if (!isConsumptionEqualInstallment(application)) {
            return loanHistory.getEndDate();
        }

        return schedules.stream()
            .filter(schedule -> !Boolean.TRUE.equals(schedule.getIsSettled()))
            .map(RepaymentSchedule::getDueDate)
            .filter(java.util.Objects::nonNull)
            .reduce((first, second) -> second)
            .orElse(loanHistory.getEndDate());
    }

    private boolean isConsumptionEqualInstallment(LoanApplication application) {
        return application.getLoanProduct() != null
            && CONSUMPTION_ANALYSIS_TYPE.equals(application.getLoanProduct().getLoanProductType())
            && EQUAL_INSTALLMENT_TYPE.equals(application.getLoanProduct().getRepaymentType());
    }

    private boolean hasRecordedPayment(RepaymentSchedule schedule) {
        return nullSafe(schedule.getPaidPrincipal()).compareTo(BigDecimal.ZERO) > 0
            || nullSafe(schedule.getPaidInterest()).compareTo(BigDecimal.ZERO) > 0;
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
            default -> throw new IllegalArgumentException("지원하지 않는 상품입니다. productKey=" + productKey);
        };
    }

    private String toProductKey(String loanProductType) {
        return switch (loanProductType) {
            case SELF_DEVELOPMENT_TYPE -> "youth-loan";
            case CONSUMPTION_ANALYSIS_TYPE -> "consumption-loan";
            default -> loanProductType;
        };
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal floorWon(BigDecimal value) {
        return nullSafe(value).setScale(0, RoundingMode.DOWN);
    }

    private BigDecimal won(BigDecimal value) {
        return WonAmount.floor(value);
    }
}
