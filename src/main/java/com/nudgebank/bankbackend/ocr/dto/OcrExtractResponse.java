package com.nudgebank.bankbackend.ocr.dto;

import java.util.List;

public record OcrExtractResponse(
        boolean success,
        String filename,
        String contentType,
        String extractedText,
        List<String> lines,
        int lineCount
) {
}
