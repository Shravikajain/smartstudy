package com.smartstudy.backend.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstudy.backend.dto.ml.MlGenerateRequest;
import com.smartstudy.backend.dto.ml.MlGenerateResponse;
import com.smartstudy.backend.dto.ml.MlGeneratedQuestion;
import com.smartstudy.backend.model.Question;
import com.smartstudy.backend.model.QuestionType;
import com.smartstudy.backend.model.Topic;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.repository.QuestionRepository;
import com.smartstudy.backend.repository.TopicRepository;
import java.util.Objects;

@Service
public class QuestionGenerationService {

    private static final int MAX_GENERATED_QUESTIONS = 50;

    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;
    private final MlClient mlClient;

    public QuestionGenerationService(TopicRepository topicRepository,
                                     QuestionRepository questionRepository,
                                     MlClient mlClient) {
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
        this.mlClient = mlClient;
    }

    @Transactional
    public List<Question> generate(Long topicId, int count, User owner) {
        if (count < 1 || count > MAX_GENERATED_QUESTIONS) {
            throw new IllegalArgumentException("Question count must be between 1 and "
                    + MAX_GENERATED_QUESTIONS);
        }
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found"));
        if (!topic.getSubject().getOwner().getId().equals(owner.getId())) {
            throw new SecurityException("Not your topic");
        }

        MlGenerateResponse generated = mlClient.generateQuestions(
                new MlGenerateRequest(topic.getSubject().getName(), topic.getName(),
                        topic.getDescription(), count));
        if (generated.questions().isEmpty()) {
            throw new IllegalStateException("ML service generated no questions");
        }

        List<Question> questions = new ArrayList<>();
        for (MlGeneratedQuestion item : generated.questions()) {
            if (item == null || item.prompt() == null || item.prompt().isBlank()
                    || !"multiple_choice".equals(item.questionType())
                    || item.options() == null || item.options().size() < 2
                    || item.correctIndex() == null || item.correctIndex() < 0
                    || item.correctIndex() >= item.options().size()) {
                throw new IllegalStateException("ML service returned an invalid question");
            }
            double difficulty = item.difficulty() - 3.0;
            questions.add(new Question(topic, item.prompt(), item.options(),
                    item.correctIndex(), item.explanation(), difficulty,
                    QuestionType.MULTIPLE_CHOICE, null));
        }
        return questionRepository.saveAll(questions);
    }

    @Transactional(readOnly = true)
    public List<Question> listForTopic(Long topicId, User owner) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found"));
        if (!Objects.equals(topic.getSubject().getOwner().getId(), owner.getId())) {
            throw new SecurityException("Not your topic");
        }
        return questionRepository.findByTopicId(topicId);
    }
}
