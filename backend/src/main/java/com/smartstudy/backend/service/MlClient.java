package com.smartstudy.backend.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.smartstudy.backend.dto.ml.MlAnalyzeRequest;
import com.smartstudy.backend.dto.ml.MlAnalyzeResponse;
import com.smartstudy.backend.dto.ml.MlDetectRequest;
import com.smartstudy.backend.dto.ml.MlDetectResponse;
import com.smartstudy.backend.dto.ml.MlExtractPdfTextResponse;
import com.smartstudy.backend.dto.ml.MlGenerateRequest;
import com.smartstudy.backend.dto.ml.MlGenerateResponse;
import com.smartstudy.backend.dto.ml.MlGradeAnswerRequest;
import com.smartstudy.backend.dto.ml.MlGradeAnswerResponse;
import com.smartstudy.backend.dto.ml.MlInterviewTurnRequest;
import com.smartstudy.backend.dto.ml.MlInterviewTurnResponse;
import com.smartstudy.backend.dto.ml.MlTranscriptionResponse;
import com.smartstudy.backend.exception.MlServiceUnavailableException;

@Component
public class MlClient {

    private final RestClient restClient;
    private final RestClient ocrRestClient;

    public MlClient(RestClient mlRestClient, @Qualifier("mlOcrRestClient") RestClient ocrRestClient) {
        this.restClient = mlRestClient;
        this.ocrRestClient = ocrRestClient;
    }

    public MlAnalyzeResponse analyze(MlAnalyzeRequest request) {
        return post("/analyze", request, MlAnalyzeResponse.class);
    }

    public MlGenerateResponse generateQuestions(MlGenerateRequest request) {
        return post("/generate-questions", request, MlGenerateResponse.class);
    }

    public MlDetectResponse detect(MlDetectRequest request) {
        return post("/detect-ai", request, MlDetectResponse.class);
    }

    public MlGradeAnswerResponse gradeAnswer(MlGradeAnswerRequest request) {
        return post("/grade-answer", request, MlGradeAnswerResponse.class);
    }

    public MlInterviewTurnResponse interviewTurn(MlInterviewTurnRequest request) {
        return post("/interview/turn", request, MlInterviewTurnResponse.class);
    }

    public MlExtractPdfTextResponse extractPdfText(byte[] pdfBytes) {
        ByteArrayResource pdfResource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return "scanned-paper.pdf";
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", pdfResource);
        return post(ocrRestClient, "/extract-pdf-text", form, MediaType.MULTIPART_FORM_DATA,
                MlExtractPdfTextResponse.class);
    }

    public MlTranscriptionResponse transcribeAudio(byte[] audioBytes, String filename,
                                                   String language) {
        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", audioResource);
        if (language != null && !language.isBlank()) {
            form.add("language", language);
        }
        return post(ocrRestClient, "/transcribe-audio", form, MediaType.MULTIPART_FORM_DATA,
                MlTranscriptionResponse.class);
    }

    private <T> T post(String uri, Object body, Class<T> responseType) {
        return post(uri, body, MediaType.APPLICATION_JSON, responseType);
    }

    private <T> T post(String uri, Object body, MediaType contentType, Class<T> responseType) {
        return post(restClient, uri, body, contentType, responseType);
    }

    private <T> T post(RestClient client, String uri, Object body, MediaType contentType,
                       Class<T> responseType) {
        try {
            return client.post()
                    .uri(uri)
                    .contentType(contentType)
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (HttpClientErrorException e) {                 // 4xx: we sent something invalid
            throw new IllegalArgumentException("ML request rejected: " + e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {                 // 5xx: the ML service or the LLM failed
            throw new MlServiceUnavailableException("ML service error: " + e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {                  // connection refused or timeout
            throw new MlServiceUnavailableException("The ML service is not reachable right now");
        } catch (RestClientException e) {                      // e.g. the response JSON didn't parse
            throw new IllegalStateException("Unexpected response from the ML service: " + e.getMessage(), e);
        }
    }
}