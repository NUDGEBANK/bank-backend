package com.nudgebank.bankbackend.deposit.service;

import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.deposit.domain.DepositAccount;
import com.nudgebank.bankbackend.deposit.domain.DepositPaymentSchedule;
import com.nudgebank.bankbackend.deposit.domain.DepositProduct;
import com.nudgebank.bankbackend.deposit.domain.DepositProductRate;
import com.nudgebank.bankbackend.deposit.domain.DepositTransaction;
import com.nudgebank.bankbackend.deposit.dto.DepositAccountActionResponse;
import com.nudgebank.bankbackend.deposit.dto.DepositAccountCreateRequest;
import com.nudgebank.bankbackend.deposit.dto.DepositAccountDetailResponse;
import com.nudgebank.bankbackend.deposit.dto.DepositAccountSummaryResponse;
import com.nudgebank.bankbackend.deposit.dto.DepositPaymentRequest;
import com.nudgebank.bankbackend.deposit.dto.DepositPaymentScheduleResponse;
import com.nudgebank.bankbackend.deposit.dto.DepositTransactionResponse;
import com.nudgebank.bankbackend.deposit.repository.DepositAccountRepository;
import com.nudgebank.bankbackend.deposit.repository.DepositPaymentScheduleRepository;
import com.nudgebank.bankbackend.deposit.repository.DepositProductRateRepository;
import com.nudgebank.bankbackend.deposit.repository.DepositProductRepository;
import com.nudgebank.bankbackend.deposit.repository.DepositTransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepositAccountService {

    private static final String PRODUCT_TYPE_FIXED_DEPOSIT = "FIXED_DEPOSIT";
    private static final String PRODUCT_TYPE_FIXED_SAVING = "FIXED_SAVING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String STATUS_EARLY_CLOSED = "EARLY_CLOSED";
    private static final String TRANSACTION_OPEN = "OPEN";
    private static final String TRANSACTION_PAY = "PAY";
    private static final String TRANSACTION_MATURITY = "MATURITY";
    private static final String TRANSACTION_EARLY_CLOSE = "EARLY_CLOSE";
    private static final String TRANSACTION_STATUS_COMPLETED = "COMPLETED";
    private static final String AUTO_TRANSFER_STATUS_READY = "READY";
    private static final String AUTO_TRANSFER_STATUS_SUCCESS = "SUCCESS";

    private final AccountRepository accountRepository;
    private final DepositProductRepository depositProductRepository;
    private final DepositProductRateRepository depositProductRateRepository;
    private final DepositAccountRepository depositAccountRepository;
    private final DepositPaymentScheduleRepository depositPaymentScheduleRepository;
    private final DepositTransactionRepository depositTransactionRepository;

    @Transactional
    public DepositAccountActionResponse create(Long memberId, DepositAccountCreateRequest request) {
        validateCreateRequest(request);

        DepositProduct product = depositProductRepository.findById(request.depositProductId())
            .orElseThrow(() -> new EntityNotFoundException("예적금 상품을 찾을 수 없습니다."));

        validateJoinConditions(product, request);

        DepositProductRate productRate = depositProductRateRepository
            .findTopByDepositProduct_DepositProductIdAndMinSavingMonthLessThanEqualAndMaxSavingMonthGreaterThanEqual(
                product.getDepositProductId(),
                request.savingMonth(),
                request.savingMonth()
            )
            .orElseThrow(() -> new IllegalArgumentException("선택한 기간에 해당하는 금리 정보가 없습니다."));

        Account linkedAccount = accountRepository.findByIdForUpdate(request.accountId())
            .filter(account -> Objects.equals(account.getMemberId(), memberId))
            .orElseThrow(() -> new EntityNotFoundException("연결할 입출금 계좌를 찾을 수 없습니다."));

        linkedAccount.withdraw(scale(request.joinAmount()));

        LocalDate startDate = LocalDate.now();
        DepositAccount depositAccount = depositAccountRepository.save(
            DepositAccount.builder()
                .depositProduct(product)
                .depositProductRate(productRate)
                .memberId(memberId)
                .account(linkedAccount)
                .depositAccountNumber(generateDepositAccountNumber())
                .joinAmount(scale(request.joinAmount()))
                .currentBalance(scale(request.joinAmount()))
                .interestRate(productRate.getInterestRate())
                .savingMonth(request.savingMonth())
                .startDate(startDate)
                .maturityDate(startDate.plusMonths(request.savingMonth()))
                .status(STATUS_ACTIVE)
                .build()
        );

        depositTransactionRepository.save(
            createTransaction(
                depositAccount,
                null,
                linkedAccount,
                TRANSACTION_OPEN,
                request.joinAmount(),
                TRANSACTION_STATUS_COMPLETED
            )
        );

        if (PRODUCT_TYPE_FIXED_SAVING.equals(product.getDepositProductType())) {
            createSavingSchedules(depositAccount, linkedAccount, request);
        }

        return new DepositAccountActionResponse(
            depositAccount.getDepositAccountId(),
            depositAccount.getStatus(),
            depositAccount.getCurrentBalance(),
            depositAccount.getJoinAmount(),
            "예적금 가입이 완료되었습니다."
        );
    }

    public List<DepositAccountSummaryResponse> getMyDepositAccounts(Long memberId) {
        return depositAccountRepository.findAllByMemberIdOrderByStartDateDesc(memberId)
            .stream()
            .map(this::toSummaryResponse)
            .toList();
    }

    public DepositAccountDetailResponse getMyDepositAccount(Long memberId, Long depositAccountId) {
        DepositAccount depositAccount = depositAccountRepository.findByDepositAccountIdAndMemberId(depositAccountId, memberId)
            .orElseThrow(() -> new EntityNotFoundException("예적금 계좌를 찾을 수 없습니다."));

        List<DepositPaymentSchedule> schedules = depositPaymentScheduleRepository
            .findAllByDepositAccount_DepositAccountIdOrderByInstallmentNoAsc(depositAccountId);
        List<DepositTransaction> transactions = depositTransactionRepository
            .findTop20ByDepositAccount_DepositAccountIdOrderByTransactionDatetimeDesc(depositAccountId);

        return new DepositAccountDetailResponse(
            depositAccount.getDepositAccountId(),
            depositAccount.getDepositProduct().getDepositProductId(),
            depositAccount.getDepositProduct().getDepositProductName(),
            depositAccount.getDepositProduct().getDepositProductType(),
            depositAccount.getDepositProduct().getDepositProductDescription(),
            depositAccount.getAccount().getAccountId(),
            depositAccount.getAccount().getAccountNumber(),
            depositAccount.getDepositAccountNumber(),
            depositAccount.getJoinAmount(),
            depositAccount.getCurrentBalance(),
            depositAccount.getInterestRate(),
            depositAccount.getSavingMonth(),
            depositAccount.getStartDate(),
            depositAccount.getMaturityDate(),
            depositAccount.getStatus(),
            depositPaymentScheduleRepository.countByDepositAccount_DepositAccountIdAndIsPaidTrue(depositAccountId),
            depositPaymentScheduleRepository.countByDepositAccount_DepositAccountId(depositAccountId),
            schedules.stream().map(this::toScheduleResponse).toList(),
            transactions.stream().map(this::toTransactionResponse).toList()
        );
    }

    @Transactional
    public DepositAccountActionResponse deposit(Long memberId, Long depositAccountId, DepositPaymentRequest request) {
        DepositAccount depositAccount = getOwnedDepositAccountForUpdate(memberId, depositAccountId);

        if (!depositAccount.isActive()) {
            throw new IllegalArgumentException("해당 예적금 계좌는 납입할 수 없는 상태입니다.");
        }
        if (PRODUCT_TYPE_FIXED_DEPOSIT.equals(depositAccount.getDepositProduct().getDepositProductType())) {
            throw new IllegalArgumentException("정기예금은 추가 납입을 지원하지 않습니다.");
        }

        DepositPaymentSchedule schedule = depositPaymentScheduleRepository
            .findFirstByDepositAccount_DepositAccountIdAndIsPaidFalseOrderByInstallmentNoAsc(depositAccountId)
            .orElseThrow(() -> new IllegalArgumentException("납입할 회차가 없습니다."));

        BigDecimal amount = scale(request.amount());
        if (amount.compareTo(scale(schedule.getPlannedAmount())) != 0) {
            throw new IllegalArgumentException("예정 납입 금액과 동일한 금액만 납입할 수 있습니다.");
        }

        Account linkedAccount = accountRepository.findByIdForUpdate(depositAccount.getAccount().getAccountId())
            .orElseThrow(() -> new EntityNotFoundException("연결 계좌를 찾을 수 없습니다."));

        linkedAccount.withdraw(amount);
        depositAccount.receivePayment(amount);
        schedule.markPaid(amount, OffsetDateTime.now());

        depositTransactionRepository.save(
            createTransaction(
                depositAccount,
                schedule,
                linkedAccount,
                TRANSACTION_PAY,
                amount,
                TRANSACTION_STATUS_COMPLETED
            )
        );

        return new DepositAccountActionResponse(
            depositAccount.getDepositAccountId(),
            depositAccount.getStatus(),
            depositAccount.getCurrentBalance(),
            amount,
            "예적금 납입이 완료되었습니다."
        );
    }

    @Transactional
    public DepositAccountActionResponse withdraw(Long memberId, Long depositAccountId) {
        DepositAccount depositAccount = getOwnedDepositAccountForUpdate(memberId, depositAccountId);
        List<DepositPaymentSchedule> schedules = depositPaymentScheduleRepository
            .findAllByDepositAccount_DepositAccountIdOrderByInstallmentNoAsc(depositAccountId);

        Account linkedAccount = accountRepository.findByIdForUpdate(depositAccount.getAccount().getAccountId())
            .orElseThrow(() -> new EntityNotFoundException("연결 계좌를 찾을 수 없습니다."));

        boolean matured = !depositAccount.getMaturityDate().isAfter(LocalDate.now());
        String nextStatus = matured ? STATUS_CLOSED : STATUS_EARLY_CLOSED;
        String transactionType = matured ? TRANSACTION_MATURITY : TRANSACTION_EARLY_CLOSE;
        BigDecimal payoutAmount = calculatePayoutAmount(depositAccount, schedules, matured);
        BigDecimal principalAmount = depositAccount.close(nextStatus);

        linkedAccount.deposit(payoutAmount);
        schedules.forEach(DepositPaymentSchedule::cancel);

        depositTransactionRepository.save(
            createTransaction(
                depositAccount,
                null,
                linkedAccount,
                transactionType,
                payoutAmount,
                TRANSACTION_STATUS_COMPLETED
            )
        );

        return new DepositAccountActionResponse(
            depositAccount.getDepositAccountId(),
            depositAccount.getStatus(),
            depositAccount.getCurrentBalance(),
            payoutAmount,
            buildWithdrawMessage(matured, payoutAmount.subtract(principalAmount))
        );
    }

    private DepositAccount getOwnedDepositAccountForUpdate(Long memberId, Long depositAccountId) {
        return depositAccountRepository.findByDepositAccountIdAndMemberIdForUpdate(depositAccountId, memberId)
            .orElseThrow(() -> new EntityNotFoundException("예적금 계좌를 찾을 수 없습니다."));
    }

    private void validateCreateRequest(DepositAccountCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("가입 요청이 올바르지 않습니다.");
        }
        if (request.depositProductId() == null) {
            throw new IllegalArgumentException("상품 정보가 필요합니다.");
        }
        if (request.accountId() == null) {
            throw new IllegalArgumentException("연결 계좌 정보가 필요합니다.");
        }
        if (request.savingMonth() == null || request.savingMonth() <= 0) {
            throw new IllegalArgumentException("가입 기간은 1개월 이상이어야 합니다.");
        }
        if (request.joinAmount() == null || request.joinAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("가입 금액은 0보다 커야 합니다.");
        }
    }

    private void validateJoinConditions(DepositProduct product, DepositAccountCreateRequest request) {
        if (request.savingMonth() < product.getMinSavingMonth() || request.savingMonth() > product.getMaxSavingMonth()) {
            throw new IllegalArgumentException("선택한 가입 기간이 상품 조건에 맞지 않습니다.");
        }

        BigDecimal joinAmount = scale(request.joinAmount());
        if (joinAmount.compareTo(scale(product.getDepositMinAmount())) < 0) {
            throw new IllegalArgumentException("최소 가입 금액보다 적은 금액으로 가입할 수 없습니다.");
        }
        if (product.getDepositMaxAmount() != null && joinAmount.compareTo(scale(product.getDepositMaxAmount())) > 0) {
            throw new IllegalArgumentException("최대 가입 금액을 초과했습니다.");
        }
        if (PRODUCT_TYPE_FIXED_SAVING.equals(product.getDepositProductType())
            && (request.monthlyPaymentAmount() == null || request.monthlyPaymentAmount().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("정기적금은 월 납입 금액이 필요합니다.");
        }
    }

    private void createSavingSchedules(DepositAccount depositAccount, Account linkedAccount, DepositAccountCreateRequest request) {
        List<DepositPaymentSchedule> schedules = new ArrayList<>();
        BigDecimal monthlyAmount = scale(request.monthlyPaymentAmount());
        boolean autoTransferEnabled = Boolean.TRUE.equals(request.autoTransferYn());

        for (int installmentNo = 1; installmentNo <= request.savingMonth(); installmentNo++) {
            boolean firstInstallment = installmentNo == 1;
            BigDecimal plannedAmount = firstInstallment ? scale(request.joinAmount()) : monthlyAmount;
            DepositPaymentSchedule.DepositPaymentScheduleBuilder builder = DepositPaymentSchedule.builder()
                .depositAccount(depositAccount)
                .account(linkedAccount)
                .installmentNo(installmentNo)
                .dueDate(depositAccount.getStartDate().plusMonths(installmentNo - 1L))
                .plannedAmount(plannedAmount)
                .isPaid(firstInstallment)
                .autoTransferYn(autoTransferEnabled)
                .autoTransferDay(request.autoTransferDay())
                .autoTransferStatus(resolveAutoTransferStatus(autoTransferEnabled, firstInstallment));

            if (firstInstallment) {
                builder.paidAmount(scale(request.joinAmount()));
                builder.paidAt(OffsetDateTime.now());
            }

            schedules.add(builder.build());
        }

        depositPaymentScheduleRepository.saveAll(schedules);
    }

    private BigDecimal calculatePayoutAmount(
        DepositAccount depositAccount,
        List<DepositPaymentSchedule> schedules,
        boolean matured
    ) {
        BigDecimal principal = scale(depositAccount.getCurrentBalance());
        if (!matured) {
            return principal;
        }

        BigDecimal interest = PRODUCT_TYPE_FIXED_DEPOSIT.equals(depositAccount.getDepositProduct().getDepositProductType())
            ? calculateFixedDepositInterest(depositAccount)
            : calculateInstallmentSavingInterest(depositAccount, schedules);

        return principal.add(interest);
    }

    private BigDecimal calculateFixedDepositInterest(DepositAccount depositAccount) {
        long depositDays = Math.max(
            0L,
            ChronoUnit.DAYS.between(depositAccount.getStartDate(), depositAccount.getMaturityDate())
        );
        return calculateSimpleInterest(depositAccount.getJoinAmount(), depositAccount.getInterestRate(), depositDays);
    }

    private BigDecimal calculateInstallmentSavingInterest(
        DepositAccount depositAccount,
        List<DepositPaymentSchedule> schedules
    ) {
        LocalDate maturityDate = depositAccount.getMaturityDate();
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (DepositPaymentSchedule schedule : schedules) {
            if (!Boolean.TRUE.equals(schedule.getIsPaid()) || schedule.getPaidAmount() == null) {
                continue;
            }

            LocalDate paidDate = schedule.getPaidAt() != null
                ? schedule.getPaidAt().toLocalDate()
                : schedule.getDueDate();
            long depositDays = Math.max(0L, ChronoUnit.DAYS.between(paidDate, maturityDate));
            totalInterest = totalInterest.add(
                calculateSimpleInterest(schedule.getPaidAmount(), depositAccount.getInterestRate(), depositDays)
            );
        }

        return scale(totalInterest);
    }

    private BigDecimal calculateSimpleInterest(BigDecimal principal, BigDecimal interestRate, long depositDays) {
        if (principal == null || interestRate == null || depositDays <= 0L) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal interest = principal
            .multiply(interestRate)
            .multiply(BigDecimal.valueOf(depositDays))
            .divide(BigDecimal.valueOf(36500L), 2, RoundingMode.HALF_UP);
        return scale(interest);
    }

    private String resolveAutoTransferStatus(boolean autoTransferEnabled, boolean firstInstallment) {
        if (!autoTransferEnabled) {
            return null;
        }
        return firstInstallment ? AUTO_TRANSFER_STATUS_SUCCESS : AUTO_TRANSFER_STATUS_READY;
    }

    private String buildWithdrawMessage(boolean matured, BigDecimal interestAmount) {
        if (!matured) {
            return "중도 해지가 완료되었습니다.";
        }

        BigDecimal scaledInterest = scale(interestAmount);
        if (scaledInterest.compareTo(BigDecimal.ZERO) <= 0) {
            return "만기 해지가 완료되었습니다.";
        }
        return "만기 해지가 완료되었습니다. (이자 " + scaledInterest.toPlainString() + "원 포함)";
    }

    private String generateDepositAccountNumber() {
        String normalized = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return "D-%s-%s-%s".formatted(
            normalized.substring(0, 4),
            normalized.substring(4, 8),
            normalized.substring(8, 12)
        );
    }

    private DepositTransaction createTransaction(
        DepositAccount depositAccount,
        DepositPaymentSchedule schedule,
        Account linkedAccount,
        String transactionType,
        BigDecimal amount,
        String status
    ) {
        return DepositTransaction.builder()
            .depositAccount(depositAccount)
            .depositPaymentSchedule(schedule)
            .account(linkedAccount)
            .transactionType(transactionType)
            .amount(scale(amount))
            .transactionDatetime(OffsetDateTime.now())
            .status(status)
            .build();
    }

    private DepositAccountSummaryResponse toSummaryResponse(DepositAccount depositAccount) {
        long paidInstallmentCount = depositPaymentScheduleRepository
            .countByDepositAccount_DepositAccountIdAndIsPaidTrue(depositAccount.getDepositAccountId());
        long totalInstallmentCount = depositPaymentScheduleRepository
            .countByDepositAccount_DepositAccountId(depositAccount.getDepositAccountId());

        return new DepositAccountSummaryResponse(
            depositAccount.getDepositAccountId(),
            depositAccount.getDepositProduct().getDepositProductId(),
            depositAccount.getDepositProduct().getDepositProductName(),
            depositAccount.getDepositProduct().getDepositProductType(),
            depositAccount.getAccount().getAccountId(),
            depositAccount.getAccount().getAccountNumber(),
            depositAccount.getDepositAccountNumber(),
            depositAccount.getJoinAmount(),
            depositAccount.getCurrentBalance(),
            depositAccount.getInterestRate(),
            depositAccount.getSavingMonth(),
            depositAccount.getStartDate(),
            depositAccount.getMaturityDate(),
            depositAccount.getStatus(),
            paidInstallmentCount,
            totalInstallmentCount
        );
    }

    private DepositPaymentScheduleResponse toScheduleResponse(DepositPaymentSchedule schedule) {
        return new DepositPaymentScheduleResponse(
            schedule.getDepositPaymentScheduleId(),
            schedule.getInstallmentNo(),
            schedule.getDueDate(),
            schedule.getPlannedAmount(),
            schedule.getPaidAmount(),
            schedule.getPaidAt(),
            schedule.getIsPaid(),
            schedule.getAutoTransferYn(),
            schedule.getAutoTransferDay(),
            schedule.getAutoTransferStatus()
        );
    }

    private DepositTransactionResponse toTransactionResponse(DepositTransaction transaction) {
        return new DepositTransactionResponse(
            transaction.getDepositTransactionId(),
            transaction.getDepositPaymentSchedule() != null
                ? transaction.getDepositPaymentSchedule().getDepositPaymentScheduleId()
                : null,
            transaction.getTransactionType(),
            transaction.getAmount(),
            transaction.getTransactionDatetime(),
            transaction.getStatus()
        );
    }

    private BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
