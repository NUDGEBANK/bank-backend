package com.nudgebank.bankbackend.finance.controller;

import com.nudgebank.bankbackend.auth.security.SecurityUtil;
import com.nudgebank.bankbackend.finance.dto.ConsumerPredictionResponse;
import com.nudgebank.bankbackend.finance.dto.ConsumptionAnalysisOverviewResponse;
import com.nudgebank.bankbackend.finance.service.ConsumptionAnalysisQueryService;
import com.nudgebank.bankbackend.finance.service.ConsumptionPredictionPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/consumption-analysis")
public class ConsumptionAnalysisController {

    private final ConsumptionAnalysisQueryService consumptionAnalysisQueryService;
    private final ConsumptionPredictionPipelineService consumptionPredictionPipelineService;

    @GetMapping("/me/overview")
    public ConsumptionAnalysisOverviewResponse getMyConsumptionOverview(Authentication authentication) {
        Long memberId = extractMemberId(authentication);
        return consumptionAnalysisQueryService.getOverview(memberId);
    }

    @PostMapping("/me/prediction/run")
    public ConsumerPredictionResponse runMyPrediction(Authentication authentication) {
        Long memberId = extractMemberId(authentication);
        try {
            consumptionPredictionPipelineService.runPredictionPipeline();
            return consumptionAnalysisQueryService.getLatestPrediction(memberId);
        } catch (IllegalStateException exception) {
            log.error("AI prediction pipeline failed for memberId={}", memberId, exception);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "AI 소비 예측 처리 중 오류가 발생했습니다.",
                exception
            );
        }
    }

    private Long extractMemberId(Authentication authentication) {
        Long memberId = SecurityUtil.extractUserId(authentication);
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return memberId;
    }
}
