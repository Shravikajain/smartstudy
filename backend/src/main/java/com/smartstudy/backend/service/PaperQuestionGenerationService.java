package com.smartstudy.backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartstudy.backend.dto.ml.MlAnalyzeResponse;
import com.smartstudy.backend.dto.ml.MlCluster;
import com.smartstudy.backend.dto.ml.MlGenerateRequest;
import com.smartstudy.backend.dto.ml.MlGeneratedQuestion;
import com.smartstudy.backend.dto.ml.MlQuestion;
import com.smartstudy.backend.model.Question;
import com.smartstudy.backend.model.QuestionType;
import com.smartstudy.backend.model.Subject;
import com.smartstudy.backend.model.Topic;
import com.smartstudy.backend.model.User;
import com.smartstudy.backend.repository.QuestionRepository;
import com.smartstudy.backend.repository.TopicRepository;

@Service
public class PaperQuestionGenerationService {

    private static final int MAX_QUESTIONS = 50;
    private static final int MAX_ML_BATCH_SIZE = 20;
    private static final int MAX_TOPICS_PER_GENERATION = 4;
    private static final int MAX_ATTEMPTS_PER_BATCH = 2;
    private static final int MAX_CONTEXT_LENGTH = 6000;

    private final SubjectService subjectService;
    private final SubjectAnalysisService analysisService;
    private final MlClient mlClient;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;

    public PaperQuestionGenerationService(SubjectService subjectService,
                                          SubjectAnalysisService analysisService,
                                          MlClient mlClient,
                                          TopicRepository topicRepository,
                                          QuestionRepository questionRepository) {
        this.subjectService = subjectService;
        this.analysisService = analysisService;
        this.mlClient = mlClient;
        this.topicRepository = topicRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional
    public GeneratedSet generate(Long subjectId, int count, User owner) {
        return generate(subjectId, count, "multiple_choice", owner);
    }

    @Transactional
    public GeneratedSet generate(Long subjectId, int count, String format, User owner) {
        if (count < 1 || count > MAX_QUESTIONS) {
            throw new IllegalArgumentException("Question count must be between 1 and " + MAX_QUESTIONS);
        }
        if (!List.of("multiple_choice", "subjective", "mixed").contains(format)) {
            throw new IllegalArgumentException(
                    "Question format must be multiple_choice, subjective, or mixed");
        }
        if (format.equals("mixed") && count < 2) {
            throw new IllegalArgumentException("Mixed format requires at least 2 questions");
        }

        Subject subject = subjectService.getSubjectByIdForUser(subjectId, owner);
        var analysisResponse = analysisService.analyze(subjectId, owner);
        MlAnalyzeResponse analysis = analysisResponse == null ? null : analysisResponse.result();
        List<MlCluster> clusters = analysis == null || analysis.clusters() == null ? List.of()
                : analysis.clusters().stream()
                        .filter(cluster -> cluster != null && cluster.questionCount() > 0)
                        .sorted(Comparator.comparingDouble(MlCluster::reappearanceScore).reversed())
                        .limit(Math.min(MAX_TOPICS_PER_GENERATION, count))
                        .toList();
        if (clusters.isEmpty()) {
            throw new IllegalArgumentException(
                    "No topics could be identified in these papers. Upload readable past-paper PDFs.");
        }

        List<String> topicNames = new ArrayList<>(clusters.size());
        for (int i = 0; i < clusters.size(); i++) {
            topicNames.add(topicName(clusters.get(i), i));
        }
        String context = topicContext(clusters, topicNames);
        List<Draft> drafts = generateDrafts(subject.getName(), topicNames, context, count, format);
        Map<String, Topic> topicsByName = new HashMap<>();
        for (Topic existing : topicRepository.findBySubjectId(subjectId)) {
            topicsByName.put(existing.getName().toLowerCase(Locale.ROOT), existing);
        }

        List<Question> questions = new ArrayList<>(drafts.size());
        for (Draft draft : drafts) {
            String key = draft.topicName().toLowerCase(Locale.ROOT);
            Topic topic = topicsByName.get(key);
            if (topic == null) {
                topic = topicRepository.save(new Topic(draft.topicName(), draft.topicDescription(), subject));
                topicsByName.put(key, topic);
            }
            MlGeneratedQuestion item = draft.question();
            boolean subjective = item.questionType().equals("subjective");
            questions.add(new Question(topic, item.prompt(), item.options(),
                    item.correctIndex() == null ? 0 : item.correctIndex(),
                    item.explanation(), item.difficulty() - 3.0,
                    subjective ? QuestionType.SUBJECTIVE : QuestionType.MULTIPLE_CHOICE,
                    item.referenceAnswer()));
        }

        List<Question> savedQuestions = questionRepository.saveAll(questions);
        List<String> inferredTopics = drafts.stream()
                .map(Draft::topicName)
                .distinct()
                .toList();
        return new GeneratedSet(inferredTopics, savedQuestions);
    }

    private List<Draft> generateDrafts(String subjectName, List<String> topicNames, String context,
                                       int count, String format) {
        List<Draft> drafts = new ArrayList<>(count);
        int baseCount = count / topicNames.size();
        int extra = count % topicNames.size();
        int questionOffset = 0;

        int multipleChoiceCount = 0;
        for (int i = 0; i < topicNames.size(); i++) {
            int topicCount = baseCount + (i < extra ? 1 : 0);
            if (topicCount == 0) {
                continue;
            }
            if (format.equals("mixed")) {
                for (int questionIndex = 0; questionIndex < topicCount; questionIndex++) {
                    if ((questionOffset + questionIndex) % 2 == 0) {
                        multipleChoiceCount++;
                    }
                }
            }
            questionOffset += topicCount;
        }
        if (format.equals("multiple_choice")) {
            multipleChoiceCount = count;
        }
        int subjectiveCount = count - multipleChoiceCount;
        generateForType(drafts, subjectName, topicNames, context,
                "multiple_choice", multipleChoiceCount, count);
        generateForType(drafts, subjectName, topicNames, context,
                "subjective", subjectiveCount, count);
        if (drafts.size() != count) {
            throw new IllegalStateException("The question generator returned "
                    + drafts.size() + " of " + count + " requested questions.");
        }
        return drafts;
    }

    private void generateForType(List<Draft> drafts, String subjectName, List<String> topicNames,
                                 String context, String questionType, int count, int totalCount) {
        int generatedCount = 0;
        while (generatedCount < count) {
            int batchTarget = Math.min(MAX_ML_BATCH_SIZE, count - generatedCount);
            int batchGenerated = 0;
            for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_BATCH && batchGenerated < batchTarget; attempt++) {
                int requested = batchTarget - batchGenerated;
                var response = mlClient.generateQuestions(
                        new MlGenerateRequest(subjectName, String.join(", ", topicNames), context,
                                requested, questionType, topicNames));
                List<MlGeneratedQuestion> generated = response == null ? null : response.questions();
                if (generated == null || generated.isEmpty()) {
                    continue;
                }
                for (MlGeneratedQuestion item : generated) {
                    if (batchGenerated == batchTarget) {
                        break;
                    }
                    if (item == null || !questionType.equals(item.questionType())) {
                        continue;
                    }
                    validate(item, questionType);
                    String assignedTopic = resolveTopicName(item.topicName(), topicNames);
                    if (assignedTopic == null) {
                        continue;
                    }
                    drafts.add(new Draft(assignedTopic,
                            "Automatically inferred from uploaded past papers.", item));
                    batchGenerated++;
                    generatedCount++;
                }
            }
            if (batchGenerated < batchTarget) {
                throw new IllegalStateException("The question generator returned "
                        + drafts.size() + " of " + totalCount
                        + " requested questions. Please try again.");
            }
        }
    }

    private String topicName(MlCluster cluster, int index) {
        List<String> keywords = cluster.keywords() == null ? List.of()
                : cluster.keywords().stream()
                        .filter(keyword -> keyword != null && !keyword.isBlank())
                        .limit(3)
                        .toList();
        return keywords.isEmpty() ? "Past-paper topic " + (index + 1) : String.join(", ", keywords);
    }

    private String topicContext(List<MlCluster> clusters, List<String> topicNames) {
        StringBuilder context = new StringBuilder(
                "Use these topic-specific past-paper examples as source material:\n");
        for (int i = 0; i < clusters.size(); i++) {
            String heading = "\nTopic: " + topicNames.get(i) + "\n";
            if (context.length() + heading.length() > MAX_CONTEXT_LENGTH) {
                break;
            }
            context.append(heading);
            List<MlQuestion> questions = clusters.get(i).questions() == null
                    ? List.of() : clusters.get(i).questions();
            for (MlQuestion question : questions.stream().limit(20).toList()) {
                if (question == null || question.text() == null || question.text().isBlank()) {
                    continue;
                }
                String text = question.text().replace("</notes>", "");
                String line = "- " + text.strip() + "\n";
                if (context.length() + line.length() > MAX_CONTEXT_LENGTH) {
                    return context.toString();
                }
                context.append(line);
            }
        }
        return context.toString();
    }

    private String resolveTopicName(String generatedTopic, List<String> topicNames) {
        if (generatedTopic == null || generatedTopic.isBlank()) {
            return topicNames.size() == 1 ? topicNames.get(0) : null;
        }
        return topicNames.stream()
                .filter(topic -> topic.equalsIgnoreCase(generatedTopic.strip()))
                .findFirst()
                .orElse(null);
    }

    private void validate(MlGeneratedQuestion item, String questionType) {
        boolean subjective = questionType.equals("subjective");
        if (item.prompt() == null || item.prompt().isBlank()
                || item.explanation() == null || item.explanation().isBlank()
                || item.difficulty() < 1 || item.difficulty() > 5
                || (subjective && (item.options() == null || !item.options().isEmpty()
                        || item.referenceAnswer() == null || item.referenceAnswer().isBlank()))
                || (!subjective && (item.options() == null || item.options().size() != 4
                        || item.options().stream().anyMatch(option -> option == null || option.isBlank())
                        || item.correctIndex() == null || item.correctIndex() < 0
                        || item.correctIndex() >= item.options().size()))) {
            throw new IllegalStateException("ML service returned an invalid question");
        }
    }

    public record GeneratedSet(List<String> topics, List<Question> questions) {}

    private record Draft(String topicName, String topicDescription, MlGeneratedQuestion question) {}
}
