package com.nudgebank.bankbackend.loan.controller;

import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import com.nudgebank.bankbackend.loan.dto.LoanApplicationCreateRequest;
import com.nudgebank.bankbackend.loan.dto.LoanApplicationRejectRequest;
import com.nudgebank.bankbackend.loan.dto.LoanApplicationSummaryResponse;
import com.nudgebank.bankbackend.loan.service.LoanApplicationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/loan-applications")
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    // 대출 신청
    @PostMapping("/submit")
    public LoanApplicationSummaryResponse submit(
            @RequestBody LoanApplicationCreateRequest request,
            Authentication authentication
    ) {
        return createApplication(request, authentication);
    }

    @PostMapping("/submit")
    public LoanApplicationSummaryResponse createLegacy(
        @RequestBody LoanApplicationCreateRequest request,
        Authentication authentication
    ) {
        return createApplication(request, authentication);
    }

    private LoanApplicationSummaryResponse createApplication(
        LoanApplicationCreateRequest request,
        Authentication authentication
    ) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return loanApplicationService.submit(memberId, request);
    }

    // 대출 승인
    @PostMapping("/{applicationId}/approve")
    public LoanApplicationSummaryResponse approve(
            @PathVariable Long applicationId,
            Authentication authentication
    ) {
        Long reviewerId = SecurityUtil.extractUserId(authentication);
        if (reviewerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return loanApplicationService.approve(applicationId, reviewerId);
    }

    // 대출 거절
    @PostMapping("/{applicationId}/reject")
    public LoanApplicationSummaryResponse reject(
            @PathVariable Long applicationId,
            @RequestBody LoanApplicationRejectRequest request,
            Authentication authentication
    ) {
        Long reviewerId = SecurityUtil.extractUserId(authentication);
        if (reviewerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return loanApplicationService.reject(applicationId, reviewerId, request.reason());
    }


//    @PostMapping
//    public LoanApplicationSummaryResponse create(
//        @RequestBody LoanApplicationCreateRequest request,
//        Authentication authentication
//    ) {
//        Long memberId = SecurityUtil.extractUserId(authentication);
//        if (memberId == null) {
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
//        }
//        return loanApplicationService.create(memberId, request);
//    }

    @GetMapping("/me")
    public List<LoanApplicationSummaryResponse> getMyApplications(Authentication authentication) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return loanApplicationService.getMyApplications(memberId);
    }
}
