package com.nudgebank.bankbackend.finance.service;

import com.nudgebank.bankbackend.auth.domain.Member;
import com.nudgebank.bankbackend.auth.repository.MemberRepository;
import com.nudgebank.bankbackend.finance.domain.AgeGroupBaseline;
import com.nudgebank.bankbackend.finance.dto.AgeGroupBaselineResponse;
import com.nudgebank.bankbackend.finance.repository.AgeGroupBaselineRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgeGroupBaselineService {

    private final MemberRepository memberRepository;
    private final AgeGroupBaselineRepository ageGroupBaselineRepository;

    public AgeGroupBaselineResponse getBaseline(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다. memberId=" + memberId));

        if (member.getBirth() == null) {
            throw new IllegalArgumentException("회원의 birth 정보가 없습니다. memberId=" + memberId);
        }

        int age = calculateAge(member.getBirth());
        String ageGroup = toAgeGroup(age);

        AgeGroupBaseline baseline = ageGroupBaselineRepository.findByAgeGroup(ageGroup)
                .orElseThrow(() -> new EntityNotFoundException("연령대 baseline이 없습니다. ageGroup=" + ageGroup));

        return AgeGroupBaselineResponse.builder()
                .memberId(member.getMemberId())
                .age(age)
                .ageGroup(baseline.getAgeGroup())
                .avgSpending(baseline.getAvgSpending())
                .essentialRatio(baseline.getEssentialRatio())
                .riskRatio(baseline.getRiskRatio())
                .volatility(baseline.getVolatility())
                .build();
    }

    private int calculateAge(LocalDate birth) {
        return Period.between(birth, LocalDate.now()).getYears();
    }

    private String toAgeGroup(int age) {
        if (age < 20) {
            return "10s";
        }
        if (age < 30) {
            return "20s";
        }
        if (age < 40) {
            return "30s";
        }
        if (age < 50) {
            return "40s";
        }
        if (age < 60) {
            return "50s";
        }
        return "60s+";
    }
}