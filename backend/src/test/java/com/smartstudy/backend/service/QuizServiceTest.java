package com.smartstudy.backend.service;

import com.smartstudy.backend.dto.AnswerResult;
import com.smartstudy.backend.dto.StartQuizResponse;
import com.smartstudy.backend.model.*;
import com.smartstudy.backend.dto.ml.MlDetectResponse;
import com.smartstudy.backend.dto.ml.MlGradeAnswerResponse;
import com.smartstudy.backend.service.MlClient;
import com.smartstudy.backend.service.PdfTextExtractor;
import com.smartstudy.backend.repository.QuestionRepository;
import com.smartstudy.backend.repository.QuizAnswerRepository;
import com.smartstudy.backend.repository.QuizSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock QuizSessionRepository sessionRepository;
    @Mock QuizAnswerRepository answerRepository;
    @Mock QuestionRepository questionRepository;
    @Mock SubjectService subjectService;
    @Mock MlClient mlClient;
    @Mock PdfTextExtractor pdfTextExtractor;

    @InjectMocks QuizService quizService;

    private User user;
    private Subject subject;

    @BeforeEach
    void setUp() {
        user = new User("a@b.com", "hash", "Alice");
        ReflectionTestUtils.setField(user, "id", 1L);
        subject = new Subject("DAA", user);
        ReflectionTestUtils.setField(subject, "id", 10L);
    }

    private Question question(long id, double difficulty, int correctIndex) {
        Question q = new Question(null, "Prompt " + id, List.of("A", "B", "C", "D"),
                correctIndex, "Because", difficulty);
        ReflectionTestUtils.setField(q, "id", id);
        return q;
    }

    private QuizSession sessionWith(Question current, int maxQuestions) {
        QuizSession session = new QuizSession(user, subject, maxQuestions);
        ReflectionTestUtils.setField(session, "id", 100L);
        session.setCurrentQuestion(current);
        return session;
    }

    @Test
    void correctAnswerRaisesAbilityAndServesTheMostInformativeNextQuestion() {
        Question q1 = question(1, 0.0, 2);
        Question q2 = question(2, 1.0, 0);
        Question q3 = question(3, -2.0, 0);
        QuizSession session = sessionWith(q1, 3);

        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(answerRepository.findBySessionId(100L))
                .thenReturn(List.of(new QuizAnswer(session, q1, 2, true, 0.0, 0.5)));
        when(questionRepository.findByTopicSubjectId(10L)).thenReturn(List.of(q1, q2, q3));

        AnswerResult result = quizService.answer(100L, 2, user);

        assertThat(result.correct()).isTrue();
        assertThat(result.abilityEstimate()).isCloseTo(0.5, within(1e-9));
        assertThat(result.answeredCount()).isEqualTo(1);
        assertThat(result.finished()).isFalse();
        assertThat(result.nextQuestion().prompt()).isEqualTo("Prompt 2");
        assertThat(result.nextQuestion().questionNumber()).isEqualTo(2);
        assertThat(q1.getTimesAnswered()).isEqualTo(1);
        assertThat(q1.getDifficulty()).isLessThan(0.0);   // answered right, so it looks easier now
        verify(answerRepository).save(any(QuizAnswer.class));
    }

    @Test
    void answeringTheLastQuestionFinishesTheQuiz() {
        Question q1 = question(1, 0.0, 2);
        QuizSession session = sessionWith(q1, 1);
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));

        AnswerResult result = quizService.answer(100L, 0, user);   // wrong: the correct index is 2

        assertThat(result.correct()).isFalse();
        assertThat(result.correctIndex()).isEqualTo(2);
        assertThat(result.finished()).isTrue();
        assertThat(result.nextQuestion()).isNull();
        assertThat(result.abilityEstimate()).isLessThan(0.0);
        assertThat(session.getStatus()).isEqualTo(QuizStatus.COMPLETED);
    }

    @Test
    void rejectsAnotherUsersQuiz() {
        QuizSession session = sessionWith(question(1, 0.0, 0), 3);
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        User other = new User("eve@b.com", "hash", "Eve");
        ReflectionTestUtils.setField(other, "id", 2L);

        assertThatThrownBy(() -> quizService.answer(100L, 0, other))
                .isInstanceOf(SecurityException.class);
        verify(answerRepository, never()).save(any());
    }

    @Test
    void rejectsAnswersToAFinishedQuiz() {
        QuizSession session = sessionWith(question(1, 0.0, 0), 3);
        session.finish();
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> quizService.answer(100L, 0, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already finished");
    }

    @Test
    void rejectsAnOptionIndexThatDoesNotExist() {
        QuizSession session = sessionWith(question(1, 0.0, 0), 3);
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> quizService.answer(100L, 4, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid option");
    }

    @Test
    void startPicksTheQuestionClosestToAverageAbility() {
        when(subjectService.getSubjectByIdForUser(10L, user)).thenReturn(subject);
        when(questionRepository.findByTopicSubjectId(10L)).thenReturn(
                List.of(question(1, -2.0, 0), question(2, 0.2, 0), question(3, 2.0, 0)));
        when(sessionRepository.save(any(QuizSession.class))).thenAnswer(inv -> inv.getArgument(0));

        StartQuizResponse response = quizService.start(10L, 5, user);

        assertThat(response.question().prompt()).isEqualTo("Prompt 2");
        assertThat(response.question().questionNumber()).isEqualTo(1);
        assertThat(response.question().totalQuestions()).isEqualTo(3);   // capped by the pool size
    }

    @Test
    void startFailsWhenTheSubjectHasNoQuestions() {
        when(subjectService.getSubjectByIdForUser(10L, user)).thenReturn(subject);
        when(questionRepository.findByTopicSubjectId(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> quizService.start(10L, 5, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No questions yet");
    }

    @Test
    void subjectiveAnswerIsGradedAndWritingEstimateDoesNotDetermineCorrectness() {
        Question q = new Question(null, "Explain the concept.", List.of(), 0,
                "Mention its key properties.", 0.0, QuestionType.SUBJECTIVE, "A reference answer.");
        ReflectionTestUtils.setField(q, "id", 1L);
        QuizSession session = sessionWith(q, 1);
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(mlClient.detect(any())).thenReturn(new MlDetectResponse(true, 0.9, 0.1,
                0.4, "Some patterns resemble AI writing."));
        when(mlClient.gradeAnswer(any())).thenReturn(
                new MlGradeAnswerResponse(0.8, "The explanation is mostly correct."));

        AnswerResult result = quizService.answerSubjective(100L,
                "This answer contains enough detail to be assessed against the expected response.", null, user);

        assertThat(result.correct()).isTrue();
        assertThat(result.score()).isEqualTo(0.8);
        assertThat(result.aiWritingAssessment().aiProbability()).isEqualTo(0.9);
        assertThat(result.correctIndex()).isNull();
        assertThat(result.finished()).isTrue();
        verify(mlClient).detect(any());
        verify(mlClient).gradeAnswer(any());
    }

    @Test
    void subjectiveAnswerRequiresEnoughTextForReliableAssessment() {
        Question q = new Question(null, "Explain the concept.", List.of(), 0,
                "Rubric", 0.0, QuestionType.SUBJECTIVE, "Reference answer");
        QuizSession session = sessionWith(q, 1);
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> quizService.answerSubjective(100L, "Too short", null, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 50 characters");
        verify(mlClient, never()).detect(any());
    }

    @Test
    void subjectiveAnswerCanBeExtractedFromAnUploadedPdf() throws Exception {
        Question q = new Question(null, "Explain the concept.", List.of(), 0,
                "Rubric", 0.0, QuestionType.SUBJECTIVE, "Reference answer");
        QuizSession session = sessionWith(q, 1);
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(pdfTextExtractor.extractText(any(byte[].class))).thenReturn(
                "This extracted answer contains enough text to be evaluated and checked for writing patterns.");
        when(mlClient.detect(any())).thenReturn(
                new MlDetectResponse(false, 0.1, 0.9, 0.4, "No strong signal."));
        when(mlClient.gradeAnswer(any())).thenReturn(new MlGradeAnswerResponse(0.7, "Correct."));
        MockMultipartFile pdf = new MockMultipartFile(
                "answerPdf", "answer.pdf", "application/pdf", new byte[] {1, 2, 3});

        AnswerResult result = quizService.answerSubjective(100L, null, pdf, user);

        assertThat(result.score()).isEqualTo(0.7);
        verify(pdfTextExtractor).extractText(any(byte[].class));
    }
}