package com.nudgebank.bankbackend.certificate.controller;

import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import com.nudgebank.bankbackend.certificate.dto.CertificateSubmissionResponse;
import com.nudgebank.bankbackend.certificate.service.CertificateSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateSubmissionController {

    private final CertificateSubmissionService certificateSubmissionService;

    @PostMapping(
            value = "/submissions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public CertificateSubmissionResponse submitCertificate(
            @RequestParam(value = "loanApplicationId", required = false) Long loanApplicationId,
            @RequestParam(value = "loanId", required = false) Long legacyLoanId,
            @RequestParam("certificateId") Long certificateId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        Long resolvedLoanApplicationId = loanApplicationId != null ? loanApplicationId : legacyLoanId;
        return certificateSubmissionService.submit(memberId, resolvedLoanApplicationId, certificateId, file);
    }
}
