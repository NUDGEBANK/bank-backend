package com.nudgebank.bankbackend.loan.service;

import com.nudgebank.bankbackend.loan.repository.LoanRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class YouthLoanAutoRepaymentScheduler {

    private static final String SELF_DEVELOPMENT_TYPE = "SELF_DEVELOPMENT";
    private static final String YOUTH_LOAN_PRODUCT_KEY = "youth-loan";

    private final LoanRepository loanRepository;
    private final LoanRepaymentService loanRepaymentService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void executeDueYouthLoanRepayments() {
        List<Long> memberIds = loanRepository.findDistinctMemberIdsByLoanProductTypeAndActive(SELF_DEVELOPMENT_TYPE);
        for (Long memberId : memberIds) {
            try {
                loanRepaymentService.executeAutoRepayment(memberId, YOUTH_LOAN_PRODUCT_KEY);
            } catch (Exception exception) {
                log.warn("Youth loan auto repayment failed. memberId={}", memberId, exception);
            }
        }
    }
}
