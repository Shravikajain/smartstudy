package com.smartstudy.backend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.smartstudy.backend.dto.AnswerResult;
import com.smartstudy.backend.dto.QuizQuestionResponse;
import com.smartstudy.backend.dto.StartQuizResponse;
import com.smartstudy.backend.dto.ml.MlDetectRequest;
import com.smartstudy.backend.dto.ml.MlDetectResponse;
import com.smartstudy.backend.dto.ml.MlGradeAnswerRequest;
import com.smartstudy.backend.dto.ml.MlGradeAnswerResponse;
import com.smartstudy.backend.engine.IrtEngine;
import com.smartstudy.backend.exception.MlServiceUnavailableException;
import com.smartstudy.backend.model.Question;
import com.smartstudy.backend.model.QuizAnswer;
import com.smartstudy.backend.model.QuizSession;
import com.smartstudy.backend.model.QuizStatus;
import com.smartstudy.backend.model.Subject;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.repository.QuestionRepository;
import com.smartstudy.backend.repository.QuizAnswerRepository;
import com.smartstudy.backend.repository.QuizSessionRepository;

@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);
    private static final int DEFAULT_QUESTION_COUNT = 10;
    private static final int MAX_QUESTION_COUNT = 50;
    private static final List<String> QUESTION_MODES = List.of("multiple_choice", "subjective", "mixed");

    private final QuizSessionRepository sessionRepository;
    private final QuizAnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final SubjectService subjectService;
    private final MlClient mlClient;
    private final PdfTextExtractor pdfTextExtractor;
    private final TransactionTemplate transactionTemplate;

    public QuizService(QuizSessionRepository sessionRepository,
                       QuizAnswerRepository answerRepository,
                       QuestionRepository questionRepository,
                       SubjectService subjectService,
                       MlClient mlClient,
                       PdfTextExtractor pdfTextExtractor,
                       PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.subjectService = subjectService;
        this.mlClient = mlClient;
        this.pdfTextExtractor = pdfTextExtractor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public StartQuizResponse start(Long subjectId, Integer requestedCount, User user) {
        return start(subjectId, requestedCount, null, user);
    }

    @Transactional
    public StartQuizResponse start(Long subjectId, Integer requestedCount,
                                   List<Long> requestedQuestionIds, User user) {
        return start(subjectId, requestedCount, requestedQuestionIds, null, user);
    }

    /**
     * @param questionMode multiple_choice, subjective or mixed; null keeps every question type
     */
    @Transactional
    public StartQuizResponse start(Long subjectId, Integer requestedCount,
                                   List<Long> requestedQuestionIds, String questionMode, User user) {
        if (questionMode != null && !QUESTION_MODES.contains(questionMode)) {
            throw new IllegalArgumentException(
                    "questionType must be multiple_choice, subjective, or mixed");
        }
        Subject subject = subjectService.getSubjectByIdForUser(subjectId, user);
        List<Question> pool;
        if (requestedQuestionIds == null) {
            pool = questionRepository.findByTopicSubjectId(subjectId);
        } else {
            if (requestedQuestionIds.isEmpty() || requestedQuestionIds.size() > MAX_QUESTION_COUNT
                    || requestedQuestionIds.stream().anyMatch(Objects::isNull)
                    || requestedQuestionIds.stream().distinct().count() != requestedQuestionIds.size()) {
                throw new IllegalArgumentException("questionIds must contain 1 to "
                        + MAX_QUESTION_COUNT + " unique question IDs");
            }
            pool = questionRepository.findByIdIn(requestedQuestionIds).stream()
                    .filter(question -> question.getTopic().getSubject().getId().equals(subjectId))
                    .toList();
            if (pool.size() != requestedQuestionIds.size()) {
                throw new IllegalArgumentException("One or more questions do not belong to this subject");
            }
        }
        if (pool.isEmpty()) {
            throw new IllegalArgumentException("No questions yet for this subject");
        }
        if ("multiple_choice".equals(questionMode) || "subjective".equals(questionMode)) {
            boolean subjective = questionMode.equals("subjective");
            pool = pool.stream().filter(question -> question.isSubjective() == subjective).toList();
            if (pool.isEmpty()) {
                throw new IllegalArgumentException("No " + (subjective ? "subjective" : "multiple-choice")
                        + " questions yet for this subject. Generate some from your past papers first.");
            }
        }

        int maxQuestions = requestedCount == null ? DEFAULT_QUESTION_COUNT : requestedCount;
        if (maxQuestions < 1 || maxQuestions > MAX_QUESTION_COUNT) {
            throw new IllegalArgumentException("maxQuestions must be between 1 and "
                    + MAX_QUESTION_COUNT);
        }
        maxQuestions = Math.min(maxQuestions, pool.size());
        Question first = IrtEngine.selectNext(0.0, pool)
                .orElseThrow(() -> new IllegalArgumentException("No questions yet for this subject"));

        QuizSession session = new QuizSession(user, subject, maxQuestions);
        if (requestedQuestionIds != null || questionMode != null) {
            session.setQuestionPool(pool);
        }
        session.setQuestionMode(modeOf(pool));
        session.setCurrentQuestion(first);
        session = sessionRepository.save(session);
        return new StartQuizResponse(session.getId(), response(first, 1, maxQuestions, null), 0.0);
    }

    @Transactional
    public AnswerResult answer(Long sessionId, Integer selectedIndex, User user) {
        QuizSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Not your quiz");
        }
        if (session.getStatus() != QuizStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Quiz is already finished");
        }
        Question current = session.getCurrentQuestion();
        if (current == null) {
            throw new IllegalArgumentException("Quiz has no current question");
        }
        if (current.isSubjective()) {
            throw new IllegalArgumentException("Submit a written answer for this subjective question");
        }
        if (selectedIndex == null || selectedIndex < 0 || selectedIndex >= current.getOptions().size()) {
            throw new IllegalArgumentException("Invalid option index");
        }

        boolean correct = selectedIndex == current.getCorrectIndex();
        return recordAnswer(session, current, selectedIndex, correct, null, null, null, null);
    }

    private static String modeOf(List<Question> pool) {
        boolean anySubjective = pool.stream().anyMatch(Question::isSubjective);
        boolean anyMultipleChoice = pool.stream().anyMatch(question -> !question.isSubjective());
        return anySubjective && anyMultipleChoice ? "mixed"
                : anySubjective ? "subjective" : "multiple_choice";
    }

    // Not @Transactional: OCR, AI detection and LLM grading can take minutes, so they run between
    // two short transactions instead of holding a database connection.
    public AnswerResult answerSubjective(Long sessionId, String answerText,
                                         MultipartFile answerPdf, User user) {
        Question question = transactionTemplate.execute(status -> {
            Question current = activeSession(sessionId, user).getCurrentQuestion();
            if (!current.isSubjective()) {
                throw new IllegalArgumentException("This question does not accept a written answer");
            }
            return current;
        });

        String answer = extractAnswer(answerText, answerPdf);
        MlDetectResponse aiAssessment = detectAiWriting(answer);
        MlGradeAnswerResponse grade = mlClient.gradeAnswer(new MlGradeAnswerRequest(
                question.getPrompt(), question.getReferenceAnswer(), question.getExplanation(), answer));
        boolean correct = grade.score() >= 0.6;

        return transactionTemplate.execute(status -> {
            QuizSession session = activeSession(sessionId, user);
            Question current = session.getCurrentQuestion();
            if (!Objects.equals(current.getId(), question.getId())) {
                throw new IllegalArgumentException("This question has already been answered");
            }
            return recordAnswer(session, current, 0, correct, answer, grade.score(), grade.feedback(),
                    aiAssessment);
        });
    }

    // The AI-writing estimate is advisory: if the detector model is unavailable, still grade the answer.
    private MlDetectResponse detectAiWriting(String answer) {
        try {
            return mlClient.detect(new MlDetectRequest(answer));
        } catch (MlServiceUnavailableException | IllegalStateException exception) {
            log.warn("AI-writing detection skipped: {}", exception.getMessage());
            return null;
        }
    }

    private String extractAnswer(String answerText, MultipartFile answerPdf) {
        boolean hasText = StringUtils.hasText(answerText);
        boolean hasPdf = answerPdf != null && !answerPdf.isEmpty();
        if (hasText == hasPdf) {
            throw new IllegalArgumentException("Provide either written text or one answer PDF");
        }

        String answer;
        if (hasPdf) {
            String filename = answerPdf.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
                throw new IllegalArgumentException("Answer upload must be a PDF file");
            }
            try {
                answer = pdfTextExtractor.extractText(answerPdf.getBytes());
            } catch (IOException exception) {
                throw new IllegalArgumentException("Could not read the answer PDF", exception);
            }
            if (!StringUtils.hasText(answer)) {
                throw new IllegalArgumentException(
                        "No selectable text was found in the PDF. Paste the answer text instead.");
            }
        } else {
            answer = answerText;
        }

        answer = answer.strip();
        if (answer.length() < 50) {
            throw new IllegalArgumentException("Answer must contain at least 50 characters to assess");
        }
        if (answer.length() > 12000) {
            throw new IllegalArgumentException("Answer must not exceed 12,000 characters");
        }
        return answer;
    }

    private QuizSession activeSession(Long sessionId, User user) {
        QuizSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found"));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Not your quiz");
        }
        if (session.getStatus() != QuizStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Quiz is already finished");
        }
        if (session.getCurrentQuestion() == null) {
            throw new IllegalArgumentException("Quiz has no current question");
        }
        return session;
    }

    private AnswerResult recordAnswer(QuizSession session, Question current, int selectedIndex,
                                      boolean correct, String answerText, Double score,
                                      String gradingFeedback, MlDetectResponse aiAssessment) {
        double abilityBefore = session.getAbilityEstimate();
        double abilityAfter = IrtEngine.updateAbility(abilityBefore, current.getDifficulty(),
                correct, session.getAnsweredCount());
        double updatedDifficulty = IrtEngine.updateDifficulty(abilityBefore, current.getDifficulty(),
                correct, session.getAnsweredCount());
        current.setDifficulty(updatedDifficulty);
        current.incrementTimesAnswered();
        questionRepository.save(current);
        QuizAnswer quizAnswer = new QuizAnswer(session, current, selectedIndex, correct,
                abilityBefore, abilityAfter);
        if (current.isSubjective()) {
            quizAnswer.recordWrittenAnswer(answerText, score, gradingFeedback, aiAssessment);
        }
        answerRepository.save(quizAnswer);
        session.recordAnswer(correct, abilityAfter);

        QuizQuestionResponse nextResponse = null;
        boolean finished = session.getAnsweredCount() >= session.getMaxQuestions();
        if (!finished) {
            Set<Long> answeredIds = new HashSet<>();
            for (QuizAnswer answer : answerRepository.findBySessionId(session.getId())) {
                answeredIds.add(answer.getQuestion().getId());
            }
            answeredIds.add(current.getId());
            List<Question> candidates = (session.getQuestionPool().isEmpty()
                    ? questionRepository.findByTopicSubjectId(session.getSubject().getId())
                    : session.getQuestionPool())
                    .stream()
                    .filter(question -> !answeredIds.contains(question.getId()))
                    .toList();
            Question next = IrtEngine.selectNext(abilityAfter, candidates).orElse(null);
            if (next == null) {
                finished = true;
            } else {
                session.setCurrentQuestion(next);
                nextResponse = response(next, session.getAnsweredCount() + 1,
                        session.getMaxQuestions(), null);
            }
        }
        if (finished) {
            session.finish();
        }
        sessionRepository.save(session);

        return new AnswerResult(correct, current.isSubjective() ? null : current.getCorrectIndex(),
                current.isSubjective() ? gradingFeedback : current.getExplanation(),
                session.getAbilityEstimate(), session.getAnsweredCount(), session.getCorrectCount(),
                finished, nextResponse, score, gradingFeedback, aiAssessment);
    }

    private QuizQuestionResponse response(Question question, int number, int total,
                                          Integer correctIndex) {
        return new QuizQuestionResponse(question.getId(), question.getPrompt(),
                question.getOptions(), number, total, correctIndex, question.getQuestionType());
    }
}
