package com.nudgebank.bankbackend.finance.service;

import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.card.domain.Card;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.card.repository.CardTransactionRepository;
import com.nudgebank.bankbackend.finance.dto.FinancialStatusResponse;
import com.nudgebank.bankbackend.loan.domain.LoanApplication;
import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import com.nudgebank.bankbackend.loan.repository.LoanApplicationRepository;
import com.nudgebank.bankbackend.loan.repository.LoanHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialStatusService {

    private final MemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final LoanHistoryRepository loanHistoryRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    public FinancialStatusResponse getFinancialStatus(Long memberId, Long transactionId) {
        validateMemberExists(memberId);

        CardTransaction transaction = cardTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "존재하지 않는 거래입니다. transactionId=" + transactionId
                ));

        Card card = transaction.getCard();

        Account account = accountRepository.findById(card.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "카드에 연결된 계좌가 없습니다. accountId=" + card.getAccountId()
                ));

        validateTransactionOwnership(memberId, account);

        BigDecimal linkedAccountBalance = nullSafe(account.getBalance());
        BigDecimal protectedBalance = nullSafe(account.getProtectedBalance());
        BigDecimal availableBalance = calculateAvailableBalance(linkedAccountBalance, protectedBalance);

        LoanHistory loanHistory = loanHistoryRepository
                .findByCard_CardIdAndStatus(card.getCardId(), "ACTIVE")
                .orElse(null);

        BigDecimal totalLoanRemainingPrincipal = loanHistory != null
                ? nullSafe(loanHistory.getRemainingPrincipal())
                : BigDecimal.ZERO;

        LoanApplication latestLoanApplication = loanApplicationRepository
                .findTopByMember_MemberIdOrderByAppliedAtDesc(memberId)
                .orElse(null);

        BigDecimal monthlyIncome = latestLoanApplication != null
                ? nullSafe(latestLoanApplication.getMonthlyIncome())
                : BigDecimal.ZERO;

        Integer salaryDate = latestLoanApplication != null
                ? latestLoanApplication.getSalaryDate()
                : null;

        Integer daysUntilPaymentDue = calculateDaysUntilPaymentDue(loanHistory);

        BigDecimal currentMonthSpendingAmount = nullSafe(
                cardTransactionRepository.sumCurrentMonthSpendingAmountUntilTransaction(
                        card.getCardId(),
                        getStartOfMonth(transaction.getTransactionDatetime()),
                        getStartOfNextMonth(transaction.getTransactionDatetime()),
                        transaction.getTransactionDatetime(),
                        transaction.getTransactionId()
                )
        );

        BigDecimal monthlyRepaidAmount = BigDecimal.ZERO;
        BigDecimal monthlyRemainingRepaymentAmount = BigDecimal.ZERO;

        return FinancialStatusResponse.builder()
                .memberId(memberId)
                .cardId(card.getCardId())
                .accountId(account.getAccountId())
                .linkedAccountBalance(linkedAccountBalance)
                .protectedBalance(protectedBalance)
                .availableBalance(availableBalance)
                .monthlyIncome(monthlyIncome)
                .salaryDate(salaryDate)
                .totalLoanRemainingPrincipal(totalLoanRemainingPrincipal)
                .monthlyRepaidAmount(monthlyRepaidAmount)
                .monthlyRemainingRepaymentAmount(monthlyRemainingRepaymentAmount)
                .daysUntilPaymentDue(daysUntilPaymentDue)
                .currentMonthSpendingAmount(currentMonthSpendingAmount)
                .build();
    }

    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new EntityNotFoundException("존재하지 않는 회원입니다. memberId=" + memberId);
        }
    }

    private void validateTransactionOwnership(Long memberId, Account account) {
        if (account == null) {
            throw new EntityNotFoundException("카드에 연결된 계좌 정보가 없습니다.");
        }

        if (!account.getMemberId().equals(memberId)) {
            throw new IllegalArgumentException(
                    "해당 거래는 요청한 회원의 거래가 아닙니다. memberId=" + memberId
            );
        }
    }

    private BigDecimal calculateAvailableBalance(BigDecimal linkedAccountBalance, BigDecimal protectedBalance) {
        BigDecimal availableBalance = linkedAccountBalance.subtract(protectedBalance);
        return availableBalance.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO
                : availableBalance;
    }

    private Integer calculateDaysUntilPaymentDue(LoanHistory nearestDueLoan) {
        if (nearestDueLoan == null || nearestDueLoan.getExpectedRepaymentDate() == null) {
            return null;
        }

        int days = (int) ChronoUnit.DAYS.between(
                LocalDate.now(),
                nearestDueLoan.getExpectedRepaymentDate()
        );

        return Math.max(days, 0);
    }

    private OffsetDateTime getStartOfMonth(OffsetDateTime baseDateTime) {
        LocalDate date = baseDateTime.toLocalDate();
        return date.withDayOfMonth(1)
                .atStartOfDay()
                .atOffset(ZoneOffset.of("+09:00"));
    }

    private OffsetDateTime getStartOfNextMonth(OffsetDateTime baseDateTime) {
        LocalDate date = baseDateTime.toLocalDate();
        return date.plusMonths(1)
                .withDayOfMonth(1)
                .atStartOfDay()
                .atOffset(ZoneOffset.of("+09:00"));
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}