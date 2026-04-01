package com.nudgebank.bankbackend.ocr.client;

import com.nudgebank.bankbackend.ocr.dto.OcrExtractResponse;
import com.nudgebank.bankbackend.ocr.exception.InvalidCertificateUploadException;
import com.nudgebank.bankbackend.ocr.exception.OcrClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

@Component
public class OcrClient {

    private static final String OCR_EXTRACT_PATH = "/ocr/extract";

    private final RestClient restClient;

    public OcrClient(
            @Value("${ocr.base-url:http://localhost:8000}") String ocrBaseUrl,
            @Value("${ocr.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${ocr.read-timeout-ms:120000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(ocrBaseUrl)
                .requestFactory(requestFactory)
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
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw new OcrClientException("OCR server response timed out", exception);
            }

            throw new OcrClientException("Failed to connect to OCR server", exception);
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

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }

            if ("ReadTimeoutException".equals(current.getClass().getSimpleName())) {
                return true;
            }

            current = current.getCause();
        }

        return false;
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
