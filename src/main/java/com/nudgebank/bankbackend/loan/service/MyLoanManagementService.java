package com.nudgebank.bankbackend.loan.service;

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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyLoanManagementService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanHistoryRepository loanHistoryRepository;
    private final LoanRepository loanRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final LoanRepaymentHistoryRepository loanRepaymentHistoryRepository;

    public MyLoanSummaryResponse getSummary(Long memberId) {
        LoanHistory loanHistory = loanHistoryRepository.findTopByMember_MemberIdOrderByCreatedAtDesc(memberId).orElse(null);
        if (loanHistory == null) {
            return buildPendingLoanSummary(memberId);
        }

        Loan loan = loanRepository.findTopByMember_MemberIdOrderByStartDateDescIdDesc(memberId).orElse(null);
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

        return new MyLoanSummaryResponse(
            loanHistory.getId(),
            loanHistory.getStatus(),
            totalPrincipal,
            remainingPrincipal,
            repaidPrincipal.max(BigDecimal.ZERO),
            loan != null ? nullSafe(loan.getInterestRate()) : BigDecimal.ZERO,
            loanHistory.getStartDate(),
            loanHistory.getEndDate(),
            nextSchedule != null ? nextSchedule.getDueDate() : loanHistory.getExpectedRepaymentDate(),
            nextSchedule != null
                ? nullSafe(nextSchedule.getPlannedPrincipal()).add(nullSafe(nextSchedule.getPlannedInterest()))
                : BigDecimal.ZERO,
            cumulativeInterest,
            loanHistory.getRepaymentAccountNumber()
        );
    }

    public List<MyLoanRepaymentScheduleResponse> getRepaymentSchedules(Long memberId) {
        LoanHistory loanHistory = loanHistoryRepository.findTopByMember_MemberIdOrderByCreatedAtDesc(memberId).orElse(null);
        if (loanHistory == null) {
            ensureDisplayableLoanExists(memberId);
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
                schedule.getOverdueDays()
            ))
            .toList();
    }

    public List<MyLoanRepaymentHistoryResponse> getRepaymentHistories(Long memberId) {
        LoanHistory loanHistory = loanHistoryRepository.findTopByMember_MemberIdOrderByCreatedAtDesc(memberId).orElse(null);
        if (loanHistory == null) {
            ensureDisplayableLoanExists(memberId);
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

    private MyLoanSummaryResponse buildPendingLoanSummary(Long memberId) {
        LoanApplication application = ensureDisplayableLoanExists(memberId);
        BigDecimal totalPrincipal = nullSafe(application.getLoanAmount());
        BigDecimal interestRate = application.getLoanProduct().getMinInterestRate() != null
            ? application.getLoanProduct().getMinInterestRate()
            : BigDecimal.ZERO;
        int repaymentMonths = application.getLoanProduct().getRepaymentPeriodMonth() != null
            && application.getLoanProduct().getRepaymentPeriodMonth() > 0
            ? application.getLoanProduct().getRepaymentPeriodMonth()
            : 1;
        LocalDate startDate = application.getAppliedAt() != null
            ? application.getAppliedAt().toLocalDate()
            : LocalDate.now();
        BigDecimal nextPaymentAmount = repaymentMonths > 0
            ? totalPrincipal.divide(BigDecimal.valueOf(repaymentMonths), 0, RoundingMode.UP)
            : totalPrincipal;

        return new MyLoanSummaryResponse(
            null,
            application.getApplicationStatus(),
            totalPrincipal,
            totalPrincipal,
            BigDecimal.ZERO,
            interestRate,
            startDate,
            startDate.plusMonths(repaymentMonths),
            startDate.plusMonths(1),
            nextPaymentAmount,
            BigDecimal.ZERO,
            null
        );
    }

    private LoanApplication ensureDisplayableLoanExists(Long memberId) {
        return loanApplicationRepository.findTopByMember_MemberIdOrderByAppliedAtDesc(memberId)
            .orElseThrow(() -> new EntityNotFoundException("대출 상품이 없습니다."));
    }

    private LoanHistory getLatestLoanHistory(Long memberId) {
        return loanHistoryRepository.findTopByMember_MemberIdOrderByCreatedAtDesc(memberId)
            .orElseThrow(() -> new EntityNotFoundException("대출 관리 정보가 없습니다."));
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
