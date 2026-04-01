package com.nudgebank.bankbackend.common.exception;

import com.nudgebank.bankbackend.ocr.exception.InvalidCertificateUploadException;
import com.nudgebank.bankbackend.ocr.exception.OcrClientException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCertificateUploadException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCertificateUpload(InvalidCertificateUploadException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        exception.getMessage(),
                        OffsetDateTime.now()
                ));
    }

    @ExceptionHandler(OcrClientException.class)
    public ResponseEntity<ErrorResponse> handleOcrClientException(OcrClientException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(
                        HttpStatus.BAD_GATEWAY.value(),
                        exception.getMessage(),
                        OffsetDateTime.now()
                ));
    }

    public record ErrorResponse(
            int status,
            String message,
            OffsetDateTime timestamp
    ) {
    }
}
