package com.nudgebank.bankbackend.loan.domain;

import com.nudgebank.bankbackend.auth.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "credit_history")
public class CreditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "credit_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "credit_grade", length = 10)
    private String creditGrade;

    @Column(name = "evaluation_result", columnDefinition = "TEXT")
    private String evaluationResult;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;
}