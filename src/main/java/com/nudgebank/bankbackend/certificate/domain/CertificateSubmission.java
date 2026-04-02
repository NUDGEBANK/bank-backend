package com.nudgebank.bankbackend.certificate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "certificate_submission")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CertificateSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "submission_id")
    private Long submissionId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "loan_application_id", nullable = false)
    private Long loanApplicationId;

    @Column(name = "certificate_id", nullable = false)
    private Long certificateId;

    @Column(name = "matched_issuer_name", length = 200)
    private String matchedIssuerName;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;

    @Column(name = "match_score", precision = 5, scale = 2)
    private java.math.BigDecimal matchScore;

    @Column(name = "detected_name", length = 100)
    private String detectedName;

    @Column(name = "name_match_yn")
    private Boolean nameMatchYn;

    @Column(name = "verification_status", length = 30)
    private String verificationStatus;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    public void updateVerification(String verificationStatus, OffsetDateTime verifiedAt) {
        this.verificationStatus = verificationStatus;
        this.verifiedAt = verifiedAt;
    }
}
