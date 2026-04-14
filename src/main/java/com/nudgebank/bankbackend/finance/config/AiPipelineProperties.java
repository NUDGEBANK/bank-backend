package com.nudgebank.bankbackend.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.pipeline")
public record AiPipelineProperties(
        String pythonCommand,
        String workingDir
) {
}