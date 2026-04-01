package com.nudgebank.bankbackend.ocr.client;

import com.nudgebank.bankbackend.ocr.dto.OcrExtractResponse;
import com.nudgebank.bankbackend.ocr.exception.InvalidCertificateUploadException;
import com.nudgebank.bankbackend.ocr.exception.OcrClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;

import java.io.IOException;

@Component
public class OcrClient {

    private static final String OCR_EXTRACT_PATH = "/ocr/extract";

    private final RestClient restClient;

    public OcrClient(@Value("${ocr.base-url:http://localhost:8000}") String ocrBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(ocrBaseUrl)
                .build();
    }

    public OcrExtractResponse extract(MultipartFile file) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new NamedByteArrayResource(file.getBytes(), file.getOriginalFilename()));

            OcrExtractResponse response = restClient.post()
                    .uri(OCR_EXTRACT_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(OcrExtractResponse.class);

            if (response == null) {
                throw new OcrClientException("OCR server returned an empty response");
            }

            return response;
        } catch (RestClientResponseException exception) {
            throw new OcrClientException(
                    "OCR server request failed with status %s: %s".formatted(
                            exception.getStatusCode(),
                            exception.getResponseBodyAsString()
                    ),
                    exception
            );
        } catch (RestClientException exception) {
            throw new OcrClientException("Failed to call OCR server", exception);
        } catch (IOException exception) {
            throw new InvalidCertificateUploadException("Failed to read upload file", exception);
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
