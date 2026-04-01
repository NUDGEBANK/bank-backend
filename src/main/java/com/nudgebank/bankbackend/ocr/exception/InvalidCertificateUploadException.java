package com.nudgebank.bankbackend.ocr.exception;

public class InvalidCertificateUploadException extends RuntimeException {

    public InvalidCertificateUploadException(String message) {
        super(message);
    }

    public InvalidCertificateUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
