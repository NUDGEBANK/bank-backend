package com.nudgebank.bankbackend.certificate.controller;

import com.nudgebank.bankbackend.certificate.dto.CertificateSubmissionResponse;
import com.nudgebank.bankbackend.certificate.service.CertificateSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
            @RequestParam("memberId") Long memberId,
            @RequestParam("loanId") Long loanId,
            @RequestParam("certificateId") Long certificateId,
            @RequestParam("file") MultipartFile file
    ) {
        return certificateSubmissionService.submit(memberId, loanId, certificateId, file);
    }
}
