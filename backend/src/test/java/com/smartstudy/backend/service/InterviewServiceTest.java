package com.smartstudy.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.smartstudy.backend.dto.ml.MlInterviewMessage;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewServiceTest {

    @Test
    void shortHistoryIsForwardedUnchanged() {
        List<MlInterviewMessage> history = List.of(
                new MlInterviewMessage("interviewer", "What is a cache?"),
                new MlInterviewMessage("candidate", "A fast store for recently used data."));

        assertThat(InterviewService.recentHistory(history)).isEqualTo(history);
    }

    @Test
    void longHistoryDropsOldestQuestionAnswerPairsUntilItFits() {
        List<MlInterviewMessage> history = new ArrayList<>();
        for (int turn = 0; turn < 5; turn++) {
            history.add(new MlInterviewMessage("interviewer", "Question " + turn));
            history.add(new MlInterviewMessage("candidate", "x".repeat(6000)));
        }

        List<MlInterviewMessage> trimmed = InterviewService.recentHistory(history);

        assertThat(trimmed).hasSize(6);
        assertThat(trimmed.get(0)).isEqualTo(new MlInterviewMessage("interviewer", "Question 2"));
        assertThat(trimmed.stream().mapToInt(message -> message.content().length()).sum())
                .isLessThanOrEqualTo(24000);
    }

    @Test
    void theQuestionBeingAnsweredIsNeverDropped() {
        List<MlInterviewMessage> history = List.of(
                new MlInterviewMessage("interviewer", "Question 1"),
                new MlInterviewMessage("candidate", "x".repeat(30000)),
                new MlInterviewMessage("interviewer", "Question 2"));

        assertThat(InterviewService.recentHistory(history))
                .containsExactly(new MlInterviewMessage("interviewer", "Question 2"));
    }
}
