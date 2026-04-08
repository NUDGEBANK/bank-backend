package com.nudgebank.bankbackend.loan.service;

import com.nudgebank.bankbackend.account.repository.AccountRepository;
import com.nudgebank.bankbackend.account.domain.Account;
import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.certificate.repository.CertificateSubmissionRepository;
import com.nudgebank.bankbackend.card.domain.Card;
import com.nudgebank.bankbackend.card.domain.CardTransaction;
import com.nudgebank.bankbackend.card.domain.Market;
import com.nudgebank.bankbackend.card.domain.MarketCategory;
import com.nudgebank.bankbackend.card.repository.CardRepository;
import com.nudgebank.bankbackend.card.repository.CardTransactionRepository;
import com.nudgebank.bankbackend.card.repository.MarketCategoryRepository;
import com.nudgebank.bankbackend.card.repository.MarketRepository;
import com.nudgebank.bankbackend.credit.domain.CreditHistory;
import com.nudgebank.bankbackend.credit.repository.CreditHistoryRepository;
import com.nudgebank.bankbackend.loan.domain.LoanApplication;
import com.nudgebank.bankbackend.loan.domain.LoanProduct;
import com.nudgebank.bankbackend.loan.dto.LoanApplicationCreateRequest;
import com.nudgebank.bankbackend.loan.dto.LoanApplicationSummaryResponse;
import com.nudgebank.bankbackend.loan.repository.LoanApplicationRepository;
import com.nudgebank.bankbackend.loan.repository.LoanProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanProductRepository loanProductRepository;
    private final CreditHistoryRepository creditHistoryRepository;
    private final MemberRepository memberRepository;
    private final CertificateSubmissionRepository certificateSubmissionRepository;
    private final AccountRepository accountRepository;
    private final CardRepository cardRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final MarketRepository marketRepository;
    private final MarketCategoryRepository marketCategoryRepository;

    public LoanApplicationSummaryResponse create(Long memberId, LoanApplicationCreateRequest request) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다. memberId=" + memberId));

        String loanProductType = toLoanProductType(request.productKey());
        LoanProduct loanProduct = loanProductRepository.findByLoanProductType(loanProductType)
            .orElseThrow(() -> new EntityNotFoundException("대출 상품을 찾을 수 없습니다. type=" + loanProductType));

        boolean alreadyApplied = loanApplicationRepository
            .findAllByMember_MemberIdOrderByAppliedAtDesc(memberId)
            .stream()
            .anyMatch(application ->
                loanProductType.equals(application.getLoanProduct().getLoanProductType()));
        if (alreadyApplied) {
            throw new IllegalArgumentException("이미 신청이 완료된 상품입니다. 내 대출 관리에서 진행 상태를 확인해 주세요.");
        }

        if (SELF_DEVELOPMENT_TYPE.equals(loanProductType)
            && accountRepository.findAllByMemberId(memberId).isEmpty()) {
            throw new IllegalArgumentException("자기계발 대출 신청을 위해 계좌가 필요합니다.");
        }

        CreditHistory creditHistory = resolveCreditHistory(memberId, loanProductType);

        String applicationStatus = "APPROVED";

        LoanApplication savedApplication = loanApplicationRepository.save(
            LoanApplication.builder()
                .loanProduct(loanProduct)
                .member(member)
                .creditHistory(creditHistory)
                .loanAmount(request.loanAmount())
                .loanTerm(request.loanTerm())
                .applicationStatus(applicationStatus)
                .appliedAt(LocalDateTime.now())
                .reviewComment(request.purpose())
                .monthlyIncome(request.monthlyIncome())
                .salaryDate(request.salaryDate())
                .build()
        );

        createLoanDisbursementTransaction(memberId, savedApplication);

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

    private void createLoanDisbursementTransaction(Long memberId, LoanApplication loanApplication) {
        Account account = accountRepository.findAllByMemberId(memberId).stream()
            .findFirst()
            .flatMap(existing -> accountRepository.findByIdForUpdate(existing.getAccountId()))
            .orElseThrow(() -> new EntityNotFoundException("대출 실행 계좌를 찾을 수 없습니다."));

        Card card = cardRepository.findByAccountId(account.getAccountId())
            .orElseThrow(() -> new EntityNotFoundException("대출 실행 내역을 연결할 카드를 찾을 수 없습니다."));

        MarketCategory loanCategory = marketCategoryRepository.findByCategoryName("대출")
            .orElseGet(() -> marketCategoryRepository.save(MarketCategory.create("대출", "LOAN")));

        Market loanMarket = marketRepository
            .findByMarketNameAndCategory_CategoryId("NudgeBank 대출 실행", loanCategory.getCategoryId())
            .orElseGet(() -> marketRepository.save(Market.create(loanCategory, "NudgeBank 대출 실행")));

        account.deposit(loanApplication.getLoanAmount());

        cardTransactionRepository.save(
            CardTransaction.builder()
                .card(card)
                .market(loanMarket)
                .category(loanCategory)
                .qrId("loan-disbursement-" + UUID.randomUUID())
                .amount(loanApplication.getLoanAmount())
                .transactionDatetime(OffsetDateTime.now())
                .menuName(loanApplication.getLoanProduct().getLoanProductName())
                .quantity(1)
                .build()
        );
    }
}
