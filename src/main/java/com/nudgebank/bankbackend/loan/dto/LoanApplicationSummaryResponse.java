package com.nudgebank.bankbackend.loan.dto;

import java.time.LocalDateTime;

public record LoanApplicationSummaryResponse(
    Long loanApplicationId,
    String productKey,
    String productName,
    String applicationStatus,
    LocalDateTime appliedAt,
    boolean requiresCertificateSubmission,
    boolean certificateSubmitted
) {}
