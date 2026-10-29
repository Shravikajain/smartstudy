package com.smartstudy.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstudy.backend.dto.AnswerReview;
import com.smartstudy.backend.dto.ProgressResponse;
import com.smartstudy.backend.dto.QuestionBankResponse;
import com.smartstudy.backend.dto.QuizHistoryItem;
import com.smartstudy.backend.dto.QuizReviewResponse;
import com.smartstudy.backend.model.Question;
import com.smartstudy.backend.model.QuizAnswer;
import com.smartstudy.backend.model.QuizSession;
import com.smartstudy.backend.model.Topic;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.repository.QuestionRepository;
import com.smartstudy.backend.repository.QuizAnswerRepository;
import com.smartstudy.backend.repository.QuizSessionRepository;

/** Past tests a learner can revisit for self-evaluation. */
@Service
public class QuizHistoryService {

    private final QuizSessionRepository sessionRepository;
    private final QuizAnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final SubjectService subjectService;

    public QuizHistoryService(QuizSessionRepository sessionRepository,
                              QuizAnswerRepository answerRepository,
                              QuestionRepository questionRepository,
                              SubjectService subjectService) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.questionRepository = questionRepository;
        this.subjectService = subjectService;
    }

    @Transactional(readOnly = true)
    public List<QuizHistoryItem> history(User user) {
        List<QuizSession> sessions = answeredSessions(user);
        Map<Long, List<QuizAnswer>> answersBySession = answersBySession(sessions);
        return sessions.stream()
                .map(session -> summary(session, answersBySession.getOrDefault(session.getId(), List.of())))
                .toList();
    }

    /** Every answered test, oldest first, plus how each topic has gone across all of them. */
    @Transactional(readOnly = true)
    public ProgressResponse progress(User user) {
        List<QuizSession> sessions = answeredSessions(user).reversed();
        Map<Long, List<QuizAnswer>> answersBySession = answersBySession(sessions);
        List<ProgressResponse.TestPoint> tests = sessions.stream().map(session -> {
            QuizHistoryItem summary = summary(session, answersBySession.getOrDefault(session.getId(), List.of()));
            return new ProgressResponse.TestPoint(session.getId(), summary.subjectId(), summary.subjectName(),
                    session.getStartedAt(), summary.answeredCount(), summary.correctCount(),
                    summary.averageWrittenScore(), summary.aiFlaggedCount(), session.getAbilityEstimate());
        }).toList();

        Map<Long, List<QuizAnswer>> answersByTopic = answersBySession.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(answer -> answer.getQuestion().getTopic().getId(),
                        LinkedHashMap::new, Collectors.toList()));
        List<ProgressResponse.TopicScore> topics = answersByTopic.values().stream().map(answers -> {
            Topic topic = answers.get(0).getQuestion().getTopic();
            return new ProgressResponse.TopicScore(topic.getId(), topic.getName(), topic.getSubject().getId(),
                    topic.getSubject().getName(), answers.size(),
                    (int) answers.stream().filter(QuizAnswer::isCorrect).count(), averageScore(answers));
        }).toList();
        return new ProgressResponse(tests, topics);
    }

    private List<QuizSession> answeredSessions(User user) {
        return sessionRepository.findByUserIdOrderByIdDesc(user.getId()).stream()
                .filter(session -> session.getAnsweredCount() > 0)
                .toList();
    }

    private Map<Long, List<QuizAnswer>> answersBySession(List<QuizSession> sessions) {
        return answerRepository
                .findBySessionIdInOrderByIdAsc(sessions.stream().map(QuizSession::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(answer -> answer.getSession().getId()));
    }

    @Transactional(readOnly = true)
    public QuizReviewResponse review(Long sessionId, User user) {
        QuizSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found"));
        if (!Objects.equals(session.getUser().getId(), user.getId())) {
            throw new SecurityException("Not your test");
        }
        List<QuizAnswer> answers = answerRepository.findBySessionIdInOrderByIdAsc(List.of(sessionId));
        List<AnswerReview> reviews = new ArrayList<>(answers.size());
        for (int i = 0; i < answers.size(); i++) {
            reviews.add(review(i + 1, answers.get(i)));
        }
        return new QuizReviewResponse(summary(session, answers), reviews);
    }

    @Transactional(readOnly = true)
    public QuestionBankResponse questionBank(Long subjectId, User user) {
        subjectService.getSubjectByIdForUser(subjectId, user);
        List<Question> questions = questionRepository.findByTopicSubjectId(subjectId);
        int subjective = (int) questions.stream().filter(Question::isSubjective).count();
        return new QuestionBankResponse(questions.size() - subjective, subjective);
    }

    private static Double averageScore(List<QuizAnswer> answers) {
        OptionalDouble writtenScores = answers.stream()
                .map(QuizAnswer::getScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();
        return writtenScores.isPresent() ? writtenScores.getAsDouble() : null;
    }

    private QuizHistoryItem summary(QuizSession session, List<QuizAnswer> answers) {
        Double averageWrittenScore = averageScore(answers);
        int aiFlagged = (int) answers.stream()
                .filter(answer -> Boolean.TRUE.equals(answer.getAiFlagged()))
                .count();
        return new QuizHistoryItem(session.getId(), session.getSubject().getId(),
                session.getSubject().getName(), session.getQuestionMode(), session.getStatus(),
                session.getStartedAt(), session.getCompletedAt(), session.getMaxQuestions(),
                session.getAnsweredCount(), session.getCorrectCount(), averageWrittenScore, aiFlagged);
    }

    private AnswerReview review(int number, QuizAnswer answer) {
        Question question = answer.getQuestion();
        boolean subjective = question.isSubjective();
        return new AnswerReview(number, question.getId(), question.getTopic().getName(),
                question.getQuestionType(), question.getPrompt(), question.getOptions(),
                subjective ? null : answer.getSelectedIndex(),
                subjective ? null : question.getCorrectIndex(),
                answer.isCorrect(), question.getExplanation(), answer.getAnswerText(),
                subjective ? question.getReferenceAnswer() : null,
                answer.getScore(), answer.getFeedback(), answer.getAiProbability(),
                answer.getAiFlagged(), answer.getAnsweredAt());
    }
}
