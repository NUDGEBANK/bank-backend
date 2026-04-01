package com.nudgebank.bankbackend.ocr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OcrExtractResponse(
        boolean success,
        String filename,
        @JsonProperty("content_type")
        String contentType,
        @JsonProperty("extracted_text")
        String extractedText,
        List<String> lines,
        @JsonProperty("line_count")
        int lineCount
) {
}
