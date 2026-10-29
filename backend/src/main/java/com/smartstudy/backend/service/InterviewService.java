package com.smartstudy.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstudy.backend.dto.InterviewReviewResponse;
import com.smartstudy.backend.dto.InterviewSummary;
import com.smartstudy.backend.dto.InterviewTurnResponse;
import com.smartstudy.backend.dto.StartInterviewRequest;
import com.smartstudy.backend.dto.ml.MlInterviewMessage;
import com.smartstudy.backend.dto.ml.MlInterviewTurnRequest;
import com.smartstudy.backend.dto.ml.MlInterviewTurnResponse;
import com.smartstudy.backend.model.InterviewAnswer;
import com.smartstudy.backend.model.InterviewSession;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.repository.InterviewAnswerRepository;
import com.smartstudy.backend.repository.InterviewSessionRepository;

/**
 * Runs interview practice and saves every question, answer and grade. The transcript sent to the
 * interviewer is rebuilt from the database, so a session can be continued after a page reload.
 */
@Service
public class InterviewService {

    private static final int MAX_HISTORY_CHARS = 24000;
    private static final List<String> TARGET_TYPES = List.of("subject", "engineering_role");
    private static final List<String> LEVELS = List.of("beginner", "intermediate", "advanced");

    private final InterviewSessionRepository sessionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final MlClient mlClient;

    public InterviewService(InterviewSessionRepository sessionRepository,
                            InterviewAnswerRepository answerRepository, MlClient mlClient) {
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.mlClient = mlClient;
    }

    @Transactional
    public InterviewTurnResponse start(StartInterviewRequest request, User user) {
        if (request.targetType() == null || !TARGET_TYPES.contains(request.targetType())) {
            throw new IllegalArgumentException("Choose a subject or engineering role");
        }
        String target = request.target() == null ? "" : request.target().trim();
        if (target.isEmpty() || target.length() > 120) {
            throw new IllegalArgumentException("Interview target must be 1 to 120 characters");
        }
        if (request.level() == null || !LEVELS.contains(request.level())) {
            throw new IllegalArgumentException("Choose beginner, intermediate, or advanced level");
        }
        if (request.questionCount() < 1 || request.questionCount() > 20) {
            throw new IllegalArgumentException("Interview question count must be between 1 and 20");
        }
        MlInterviewTurnResponse opening = mlClient.interviewTurn(new MlInterviewTurnRequest(
                request.targetType(), target, request.level(), 0, request.questionCount(), List.of(), null));
        if (isBlank(opening.question()) || opening.complete()) {
            throw new IllegalArgumentException("The interviewer could not prepare an opening question. Please try again.");
        }
        InterviewSession session = new InterviewSession(user, request.targetType(), target,
                request.level(), request.questionCount());
        session.askNext(opening.question());
        sessionRepository.save(session);
        return new InterviewTurnResponse(session.getId(), session.getQuestionNumber(), session.getQuestionCount(),
                session.getCurrentQuestion(), "", 0.0, null, false);
    }

    // Nothing is saved if the interviewer fails, so the learner can submit the same answer again.
    @Transactional
    public InterviewTurnResponse answer(Long sessionId, String answer, User user) {
        InterviewSession session = owned(sessionId, user);
        if (session.isCompleted()) {
            throw new IllegalArgumentException("This interview has already finished");
        }
        String text = answer == null ? "" : answer.trim();
        if (text.length() < 10 || text.length() > 12000) {
            throw new IllegalArgumentException("Answers must be between 10 and 12,000 characters");
        }
        List<MlInterviewMessage> transcript = new ArrayList<>();
        for (InterviewAnswer previous : answerRepository.findBySessionIdOrderByQuestionNumberAsc(sessionId)) {
            transcript.add(new MlInterviewMessage("interviewer", previous.getQuestion()));
            transcript.add(new MlInterviewMessage("candidate", previous.getAnswer()));
        }
        transcript.add(new MlInterviewMessage("interviewer", session.getCurrentQuestion()));
        MlInterviewTurnResponse reply = mlClient.interviewTurn(new MlInterviewTurnRequest(
                session.getTargetType(), session.getTarget(), session.getLevel(), session.getQuestionNumber(),
                session.getQuestionCount(), recentHistory(transcript), text));

        boolean last = reply.complete() || session.getQuestionNumber() >= session.getQuestionCount();
        if (!last && isBlank(reply.question())) {
            throw new IllegalArgumentException("The interviewer did not provide the next question. Please try again.");
        }
        double score = Math.max(0.0, Math.min(1.0, reply.score()));
        answerRepository.save(new InterviewAnswer(session, session.getQuestionNumber(),
                session.getCurrentQuestion(), text, score, reply.feedback()));
        session.recordAnswer(score);
        if (last) {
            session.finish(reply.feedback());
        } else {
            session.askNext(reply.question());
        }
        return new InterviewTurnResponse(session.getId(), session.getQuestionNumber(), session.getQuestionCount(),
                session.getCurrentQuestion(), reply.feedback(), score, session.getAverageScore(), last);
    }

    @Transactional(readOnly = true)
    public List<InterviewSummary> history(User user) {
        return sessionRepository.findByUserIdOrderByIdDesc(user.getId()).stream()
                .map(InterviewService::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public InterviewReviewResponse review(Long sessionId, User user) {
        InterviewSession session = owned(sessionId, user);
        List<InterviewReviewResponse.Answer> answers = answerRepository
                .findBySessionIdOrderByQuestionNumberAsc(sessionId).stream()
                .map(answer -> new InterviewReviewResponse.Answer(answer.getQuestionNumber(), answer.getQuestion(),
                        answer.getAnswer(), answer.getScore(), answer.getFeedback(), answer.getAnsweredAt()))
                .toList();
        return new InterviewReviewResponse(summary(session), session.getQuestionNumber(),
                session.getCurrentQuestion(), session.getSummaryFeedback(), answers);
    }

    // Long interviews would otherwise fail near the end: keep the most recent messages that fit
    // the prompt budget, dropping whole interviewer/candidate pairs from the start.
    static List<MlInterviewMessage> recentHistory(List<MlInterviewMessage> history) {
        int start = 0;
        int length = history.stream().mapToInt(message -> message.content().length()).sum();
        while (length > MAX_HISTORY_CHARS && start < history.size() - 1) {
            int dropped = Math.min(2, history.size() - 1 - start);
            for (int i = 0; i < dropped; i++) {
                length -= history.get(start + i).content().length();
            }
            start += dropped;
        }
        return history.subList(start, history.size());
    }

    private InterviewSession owned(Long sessionId, User user) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found"));
        if (!Objects.equals(session.getUser().getId(), user.getId())) {
            throw new SecurityException("Not your interview");
        }
        return session;
    }

    private static InterviewSummary summary(InterviewSession session) {
        return new InterviewSummary(session.getId(), session.getTargetType(), session.getTarget(),
                session.getLevel(), session.getQuestionCount(), session.getAnsweredCount(),
                session.getAverageScore(), session.isCompleted(), session.getStartedAt(), session.getCompletedAt());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
