package com.smartstudy.backend.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.smartstudy.backend.dto.AuthenticityCheckResponse;
import com.smartstudy.backend.dto.ml.MlDetectRequest;
import com.smartstudy.backend.dto.ml.MlDetectResponse;
import com.smartstudy.backend.dto.ml.MlPassageScore;
import com.smartstudy.backend.model.AuthenticityCheck;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.repository.AuthenticityCheckRepository;

/** Checks a learner's own material (pasted text, PDF or plain-text file) for AI-written content. */
@Service
public class AuthenticityService {

    static final int MIN_CHARACTERS = 50;
    static final int MAX_CHARACTERS = 60000;
    // Same threshold the ML service uses to flag the document as a whole.
    private static final double FLAG_THRESHOLD = 0.6;

    private final MlClient mlClient;
    private final PdfTextExtractor pdfTextExtractor;
    private final AuthenticityCheckRepository checkRepository;

    public AuthenticityService(MlClient mlClient, PdfTextExtractor pdfTextExtractor,
                               AuthenticityCheckRepository checkRepository) {
        this.mlClient = mlClient;
        this.pdfTextExtractor = pdfTextExtractor;
        this.checkRepository = checkRepository;
    }

    // Not @Transactional: PDF OCR and detection can be slow; only the final save touches the database.
    public AuthenticityCheckResponse check(String text, MultipartFile file, User user) {
        boolean hasText = StringUtils.hasText(text);
        boolean hasFile = file != null && !file.isEmpty();
        if (hasText == hasFile) {
            throw new IllegalArgumentException("Paste your text or upload one file, not both");
        }

        String content = hasFile ? readFile(file) : text;
        String sourceName = hasFile ? file.getOriginalFilename() : "Pasted text";
        content = content.strip();
        if (content.length() < MIN_CHARACTERS) {
            throw new IllegalArgumentException(
                    "Provide at least " + MIN_CHARACTERS + " characters of text to check");
        }
        if (content.length() > MAX_CHARACTERS) {
            throw new IllegalArgumentException(
                    "The text is too long to check at once (60,000 characters maximum). Check one section at a time.");
        }

        MlDetectResponse result = mlClient.detect(new MlDetectRequest(content));
        List<MlPassageScore> passages = result.passages();
        int flagged = (int) passages.stream()
                .filter(passage -> passage.aiProbability() >= FLAG_THRESHOLD)
                .count();
        AuthenticityCheck saved = checkRepository.save(new AuthenticityCheck(user,
                sourceName == null || sourceName.isBlank() ? "Uploaded file" : sourceName,
                content.length(), result.aiProbability(), result.humanProbability(),
                result.isAiGenerated(), result.burstinessScore(), result.feedback(),
                passages.size(), flagged));
        return toResponse(saved, passages);
    }

    public List<AuthenticityCheckResponse> recent(User user) {
        return checkRepository.findTop20ByUserIdOrderByIdDesc(user.getId()).stream()
                .map(check -> toResponse(check, List.of()))
                .toList();
    }

    private String readFile(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".pdf")) {
                return pdfTextExtractor.extractText(file.getBytes());
            }
            if (name.endsWith(".txt") || name.endsWith(".md")) {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(file.getBytes()))
                        .toString();
            }
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Text files must be saved as UTF-8", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read the uploaded file", exception);
        }
        throw new IllegalArgumentException("Upload a PDF, .txt or .md file");
    }

    private static AuthenticityCheckResponse toResponse(AuthenticityCheck check,
                                                        List<MlPassageScore> passages) {
        return new AuthenticityCheckResponse(check.getId(), check.getSourceName(),
                check.getCharacterCount(), check.getCreatedAt(), check.getAiProbability(),
                check.getHumanProbability(), check.isAiGenerated(), check.getBurstinessScore(),
                check.getFeedback(), check.getPassageCount(), check.getFlaggedPassageCount(), passages);
    }
}
