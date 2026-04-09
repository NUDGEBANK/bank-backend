package com.nudgebank.bankbackend.loan.service;

import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.card.domain.Card;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.card.domain.Market;
import com.nudgebank.bankbackend.card.domain.MarketCategory;
import com.nudgebank.bankbackend.card.repository.CardRepository;
import com.nudgebank.bankbackend.card.repository.CardTransactionRepository;
import com.nudgebank.bankbackend.card.repository.MarketCategoryRepository;
import com.nudgebank.bankbackend.card.repository.MarketRepository;
import com.nudgebank.bankbackend.certificate.repository.CertificateSubmissionRepository;
import com.nudgebank.bankbackend.credit.domain.CreditHistory;
import com.nudgebank.bankbackend.credit.repository.CreditHistoryRepository;
import com.nudgebank.bankbackend.loan.domain.Loan;
import com.nudgebank.bankbackend.loan.domain.LoanApplication;
import com.nudgebank.bankbackend.loan.domain.LoanHistory;
import com.nudgebank.bankbackend.loan.domain.LoanProduct;
import com.nudgebank.bankbackend.loan.domain.RepaymentSchedule;
import com.nudgebank.bankbackend.loan.dto.LoanApplicationCreateRequest;
import com.nudgebank.bankbackend.loan.dto.LoanApplicationSummaryResponse;
import com.nudgebank.bankbackend.loan.repository.LoanApplicationRepository;
import com.nudgebank.bankbackend.loan.repository.LoanHistoryRepository;
import com.nudgebank.bankbackend.loan.repository.LoanProductRepository;
import com.nudgebank.bankbackend.loan.repository.LoanRepository;
import com.nudgebank.bankbackend.loan.repository.RepaymentScheduleRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LoanApplicationService {

    private static final String SELF_DEVELOPMENT_TYPE = "SELF_DEVELOPMENT";
    private static final String CONSUMPTION_ANALYSIS_TYPE = "CONSUMPTION_ANALYSIS";
    private static final String EMERGENCY_TYPE = "EMERGENCY";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String APPROVED_STATUS = "APPROVED";

    private static final String LOAN_CATEGORY_NAME = "대출";
    private static final String LOAN_MARKET_NAME = "NudgeBank 대출 실행";
    private static final String LOAN_CATEGORY_MCC = "LOAN";

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanRepository loanRepository;
    private final LoanHistoryRepository loanHistoryRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final CreditHistoryRepository creditHistoryRepository;
    private final MemberRepository memberRepository;
    private final CertificateSubmissionRepository certificateSubmissionRepository;
    private final AccountRepository accountRepository;
    private final CardRepository cardRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final MarketRepository marketRepository;
    private final MarketCategoryRepository marketCategoryRepository;

    public LoanApplicationSummaryResponse create(Long memberId, LoanApplicationCreateRequest request) {
        Member member = memberRepository.findByIdForUpdate(memberId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다. memberId=" + memberId));

        String loanProductType = toLoanProductType(request.productKey());
        LoanProduct loanProduct = loanProductRepository.findByLoanProductType(loanProductType)
            .orElseThrow(() -> new EntityNotFoundException("대출 상품을 찾을 수 없습니다. type=" + loanProductType));

        boolean alreadyApplied = loanApplicationRepository
            .findAllByMember_MemberIdOrderByAppliedAtDesc(member.getMemberId())
            .stream()
            .anyMatch(application -> loanProductType.equals(application.getLoanProduct().getLoanProductType()));
        if (alreadyApplied) {
            throw new IllegalArgumentException("이미 신청이 완료된 상품입니다. 내 대출 관리에서 진행 상태를 확인해 주세요.");
        }

        if (SELF_DEVELOPMENT_TYPE.equals(loanProductType)
            && accountRepository.findAllByMemberId(member.getMemberId()).isEmpty()) {
            throw new IllegalArgumentException("자기계발 대출 신청을 위해 계좌가 필요합니다.");
        }

        CreditHistory creditHistory = resolveCreditHistory(member.getMemberId(), loanProductType);

        LoanApplication savedApplication = loanApplicationRepository.save(
            LoanApplication.builder()
                .loanProduct(loanProduct)
                .member(member)
                .creditHistory(creditHistory)
                .loanAmount(request.loanAmount())
                .loanTerm(request.loanTerm())
                .applicationStatus(APPROVED_STATUS)
                .appliedAt(LocalDateTime.now())
                .reviewComment(request.purpose())
                .monthlyIncome(request.monthlyIncome())
                .salaryDate(request.salaryDate())
                .build()
        );

        createLoanExecution(savedApplication);
        return toSummary(savedApplication);
    }

    @Transactional(readOnly = true)
    public List<LoanApplicationSummaryResponse> getMyApplications(Long memberId) {
        return loanApplicationRepository.findAllByMember_MemberIdOrderByAppliedAtDesc(memberId).stream()
            .map(this::toSummary)
            .toList();
    }

    private CreditHistory resolveCreditHistory(Long memberId, String loanProductType) {
        return creditHistoryRepository.findTopByMemberIdOrderByEvaluatedAtDescCreditHistoryIdDesc(memberId)
            .orElseGet(() -> {
                if (!SELF_DEVELOPMENT_TYPE.equals(loanProductType)) {
                    throw new EntityNotFoundException("대출 심사를 위한 신용 정보가 없습니다.");
                }

                return creditHistoryRepository.save(
                    CreditHistory.create(
                        memberId,
                        null,
                        null,
                        "SELF_DEVELOPMENT_APPLICATION_INITIALIZED",
                        LocalDateTime.now()
                    )
                );
            });
    }

    private void createLoanExecution(LoanApplication loanApplication) {
        Account disbursementAccount = resolveDisbursementAccount(loanApplication.getMember().getMemberId());
        Card card = cardRepository.findByAccountId(disbursementAccount.getAccountId())
            .orElseThrow(() -> new EntityNotFoundException("대출 실행에 사용할 카드가 없습니다."));

        String qrId = "loan-disbursement-" + loanApplication.getId();
        if (cardTransactionRepository.existsByQrId(qrId)) {
            return;
        }

        LocalDate startDate = loanApplication.getAppliedAt() != null
            ? loanApplication.getAppliedAt().toLocalDate()
            : LocalDate.now();
        int repaymentMonths = resolveRepaymentMonths(loanApplication);
        LocalDate endDate = startDate.plusMonths(repaymentMonths);
        BigDecimal principalAmount = nullSafe(loanApplication.getLoanAmount());
        BigDecimal interestRate = loanApplication.getLoanProduct().getMinInterestRate() != null
            ? loanApplication.getLoanProduct().getMinInterestRate()
            : BigDecimal.ZERO;

        loanRepository.save(
            Loan.builder()
                .loanApplication(loanApplication)
                .member(loanApplication.getMember())
                .principalAmount(principalAmount)
                .interestRate(interestRate)
                .startDate(startDate)
                .endDate(endDate)
                .status(ACTIVE_STATUS)
                .build()
        );

        LoanHistory loanHistory = loanHistoryRepository.save(
            LoanHistory.create(
                loanApplication.getMember(),
                card,
                principalAmount,
                disbursementAccount.getAccountNumber(),
                principalAmount,
                startDate,
                endDate,
                ACTIVE_STATUS,
                startDate.plusMonths(1),
                OffsetDateTime.now()
            )
        );

        repaymentScheduleRepository.saveAll(
            buildRepaymentSchedules(loanHistory, principalAmount, interestRate, startDate, repaymentMonths)
        );

        disbursementAccount.deposit(principalAmount);

        MarketCategory category = resolveLoanCategory();
        Market market = resolveLoanMarket(category);

        CardTransaction transaction = CardTransaction.builder()
            .card(card)
            .market(market)
            .qrId(qrId)
            .category(category)
            .amount(principalAmount)
            .transactionDatetime(OffsetDateTime.now())
            .menuName(loanApplication.getLoanProduct().getLoanProductName())
            .quantity(1)
            .build();

        cardTransactionRepository.save(transaction);
    }

    private Account resolveDisbursementAccount(Long memberId) {
        List<Account> accounts = accountRepository.findAllByMemberId(memberId);

        if (accounts.isEmpty()) {
            throw new EntityNotFoundException("대출 실행 계좌를 찾을 수 없습니다.");
        }

        if (accounts.size() > 1) {
            throw new IllegalArgumentException("입금 대상 계좌가 여러 개입니다. 대출 실행 계좌를 명시해 주세요.");
        }

        Long accountId = accounts.get(0).getAccountId();
        return accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new EntityNotFoundException("대출 실행 계좌를 찾을 수 없습니다."));
    }

    private synchronized MarketCategory resolveLoanCategory() {
        return marketCategoryRepository.findByCategoryName(LOAN_CATEGORY_NAME)
            .orElseGet(() -> marketCategoryRepository.save(MarketCategory.create(LOAN_CATEGORY_NAME, LOAN_CATEGORY_MCC)));
    }

    private synchronized Market resolveLoanMarket(MarketCategory category) {
        return marketRepository.findByMarketNameAndCategory_CategoryId(LOAN_MARKET_NAME, category.getCategoryId())
            .orElseGet(() -> marketRepository.save(Market.create(category, LOAN_MARKET_NAME)));
    }

    private List<RepaymentSchedule> buildRepaymentSchedules(
        LoanHistory loanHistory,
        BigDecimal principalAmount,
        BigDecimal annualInterestRate,
        LocalDate startDate,
        int repaymentMonths
    ) {
        List<RepaymentSchedule> schedules = new ArrayList<>();
        BigDecimal remainingPrincipal = principalAmount;
        BigDecimal monthlyPrincipal = principalAmount
            .divide(BigDecimal.valueOf(repaymentMonths), 2, RoundingMode.DOWN);
        BigDecimal allocatedPrincipal = BigDecimal.ZERO;

        for (int month = 1; month <= repaymentMonths; month++) {
            BigDecimal plannedPrincipal = month == repaymentMonths
                ? principalAmount.subtract(allocatedPrincipal)
                : monthlyPrincipal;
            BigDecimal plannedInterest = remainingPrincipal
                .multiply(annualInterestRate)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

            schedules.add(
                RepaymentSchedule.create(
                    loanHistory,
                    startDate.plusMonths(month),
                    plannedPrincipal,
                    plannedInterest
                )
            );

            allocatedPrincipal = allocatedPrincipal.add(plannedPrincipal);
            remainingPrincipal = remainingPrincipal.subtract(plannedPrincipal).max(BigDecimal.ZERO);
        }

        return schedules;
    }

    private int resolveRepaymentMonths(LoanApplication loanApplication) {
        Integer repaymentPeriodMonth = loanApplication.getLoanProduct().getRepaymentPeriodMonth();
        if (repaymentPeriodMonth != null && repaymentPeriodMonth > 0) {
            return repaymentPeriodMonth;
        }

        String loanTerm = loanApplication.getLoanTerm();
        if (loanTerm != null) {
            String digits = loanTerm.replaceAll("[^0-9]", "");
            if (!digits.isBlank()) {
                return Integer.parseInt(digits);
            }
        }

        return 12;
    }

    private LoanApplicationSummaryResponse toSummary(LoanApplication loanApplication) {
        boolean preferentialRateVerificationAvailable =
            SELF_DEVELOPMENT_TYPE.equals(loanApplication.getLoanProduct().getLoanProductType());
        boolean requiresCertificateSubmission = false;
        var latestSubmission = certificateSubmissionRepository
            .findTopByLoanApplicationIdOrderBySubmittedAtDescSubmissionIdDesc(loanApplication.getId());
        boolean certificateSubmitted = latestSubmission.isPresent();
        String preferentialRateVerificationStatus = latestSubmission
            .map(submission -> submission.getVerificationStatus())
            .orElse(null);

        return new LoanApplicationSummaryResponse(
            loanApplication.getId(),
            toProductKey(loanApplication.getLoanProduct().getLoanProductType()),
            loanApplication.getLoanProduct().getLoanProductName(),
            loanApplication.getApplicationStatus(),
            loanApplication.getAppliedAt(),
            requiresCertificateSubmission,
            certificateSubmitted,
            preferentialRateVerificationAvailable,
            certificateSubmitted,
            preferentialRateVerificationStatus
        );
    }

    private String toLoanProductType(String productKey) {
        return switch (productKey) {
            case "youth-loan" -> SELF_DEVELOPMENT_TYPE;
            case "consumption-loan" -> CONSUMPTION_ANALYSIS_TYPE;
            case "situate-loan" -> EMERGENCY_TYPE;
            default -> throw new IllegalArgumentException("지원하지 않는 상품입니다. productKey=" + productKey);
        };
    }

    private String toProductKey(String loanProductType) {
        return switch (loanProductType) {
            case SELF_DEVELOPMENT_TYPE -> "youth-loan";
            case CONSUMPTION_ANALYSIS_TYPE -> "consumption-loan";
            case EMERGENCY_TYPE -> "situate-loan";
            default -> loanProductType;
        };
    }

    private BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
