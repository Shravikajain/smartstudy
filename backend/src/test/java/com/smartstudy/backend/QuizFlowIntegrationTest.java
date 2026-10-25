package com.smartstudy.backend;

import com.jayway.jsonpath.JsonPath;
import com.smartstudy.backend.dto.SubjectAnalysisResponse;
import com.smartstudy.backend.dto.ml.MlAnalyzeResponse;
import com.smartstudy.backend.service.SubjectAnalysisService;
import com.smartstudy.backend.dto.ml.MlGenerateRequest;
import com.smartstudy.backend.dto.ml.MlGeneratedQuestion;
import com.smartstudy.backend.dto.ml.MlGenerateResponse;
import com.smartstudy.backend.dto.ml.MlDetectResponse;
import com.smartstudy.backend.dto.ml.MlGradeAnswerResponse;
import com.smartstudy.backend.dto.ml.MlInterviewTurnRequest;
import com.smartstudy.backend.dto.ml.MlInterviewTurnResponse;
import com.smartstudy.backend.dto.ml.MlTranscriptionResponse;
import com.smartstudy.backend.dto.ml.MlCluster;
import com.smartstudy.backend.dto.ml.MlQuestion;
import com.smartstudy.backend.model.Question;
import com.smartstudy.backend.model.QuestionType;
import com.smartstudy.backend.model.Subject;
import com.smartstudy.backend.model.Topic;
import com.smartstudy.backend.repository.QuestionRepository;
import com.smartstudy.backend.repository.SubjectRepository;
import com.smartstudy.backend.repository.TopicRepository;
import com.smartstudy.backend.service.MlClient;
import com.smartstudy.backend.service.QuestionPaperService;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuizFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired SubjectRepository subjectRepository;
    @Autowired TopicRepository topicRepository;
    @Autowired QuestionRepository questionRepository;

    @MockitoBean MlClient mlClient;   // replaces the real ML-service client with a fake
    @MockitoBean SubjectAnalysisService analysisService;
    @MockitoBean QuestionPaperService paperService;

    // ---------- helpers ----------

    private String postJson(String url, String auth, String body, Object... uriVars) throws Exception {
        var request = post(url, uriVars).contentType(MediaType.APPLICATION_JSON).content(body);
        if (auth != null) {
            request.header("Authorization", auth);
        }
        return mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String registerAndLogin(String email) throws Exception {
        postJson("/api/auth/register", null,
                "{\"email\":\"" + email + "\",\"password\":\"pass123\",\"fullName\":\"Test User\"}");
        String loginBody = postJson("/api/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"pass123\"}");
        String token = JsonPath.read(loginBody, "$.token");
        return "Bearer " + token;
    }

    private ResultActions answer(String auth, Integer sessionId, String body) throws Exception {
        return mockMvc.perform(post("/api/quiz/{id}/answer", sessionId)
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // ---------- tests ----------

    @Test
    void fullAdaptiveQuizFlow() throws Exception {
        String auth = registerAndLogin("flow@test.com");

        Integer subjectId = JsonPath.read(postJson("/api/subjects", auth, "{\"name\":\"DAA\"}"), "$.id");
        Integer topicId = JsonPath.read(postJson("/api/subjects/{id}/topics", auth,
                "{\"name\":\"Backtracking\",\"description\":\"N-Queens\"}", subjectId), "$.id");

        when(mlClient.generateQuestions(any())).thenReturn(new MlGenerateResponse(List.of(
                new MlGeneratedQuestion("Medium question", List.of("A", "B", "C", "D"), 1, "Because", 3),
                new MlGeneratedQuestion("Hard question", List.of("A", "B", "C", "D"), 2, "Because", 5),
                new MlGeneratedQuestion("Easy question", List.of("A", "B", "C", "D"), 0, "Because", 1))));

        mockMvc.perform(post("/api/topics/{id}/questions/generate", topicId)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/topics/{id}/questions", topicId)
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].correctIndex").exists());
        var generationRequest = forClass(MlGenerateRequest.class);
        verify(mlClient).generateQuestions(generationRequest.capture());
        assertThat(generationRequest.getValue().count()).isEqualTo(3);

        // Start: a brand-new student (ability 0) should get the medium question, and no answer is leaked.
        String startBody = mockMvc.perform(post("/api/subjects/{id}/quiz", subjectId)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxQuestions\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question.prompt").value("Medium question"))
                .andExpect(jsonPath("$.question.correctIndex").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Integer sessionId = JsonPath.read(startBody, "$.sessionId");

        // Right answer -> ability rises -> the harder question comes next.
        answer(auth, sessionId, "{\"selectedIndex\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.abilityEstimate").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.nextQuestion.prompt").value("Hard question"));

        // Bad payloads are rejected by validation.
        answer(auth, sessionId, "{}").andExpect(status().isBadRequest());
        answer(auth, sessionId, "{\"selectedIndex\":9}").andExpect(status().isBadRequest());

        // Wrong answer -> only the easy question is left.
        answer(auth, sessionId, "{\"selectedIndex\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.correctIndex").value(2))
                .andExpect(jsonPath("$.nextQuestion.prompt").value("Easy question"));

        // Last answer finishes the quiz.
        answer(auth, sessionId, "{\"selectedIndex\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.nextQuestion").value(nullValue()))
                .andExpect(jsonPath("$.answeredCount").value(3))
                .andExpect(jsonPath("$.correctCount").value(2));

        // Answering a finished quiz is an error.
        answer(auth, sessionId, "{\"selectedIndex\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("already finished")));
    }

    @Test
    void subjectAnalysisRoutesReturnTheFrontendContract() throws Exception {
        String auth = registerAndLogin("analysis@test.com");
        Integer subjectId = JsonPath.read(postJson("/api/subjects", auth, "{\"name\":\"DAA\"}"), "$.id");
        when(analysisService.analyze(any(), any())).thenReturn(new SubjectAnalysisResponse(
                new MlAnalyzeResponse(0, 1, List.of(), List.of()), LocalDateTime.parse("2026-10-21T12:00:00")));

        mockMvc.perform(get("/api/subjects/{id}/analysis", subjectId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalQuestions").value(0))
                .andExpect(jsonPath("$.result.totalPapers").value(1))
                .andExpect(jsonPath("$.analyzedAt").value("2026-10-21T12:00:00"));
        mockMvc.perform(post("/api/subjects/{id}/analysis", subjectId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalPapers").value(1));
    }

    @Test
    void uploadedPapersGenerateQuestionsThatFeedTheAdaptiveQuiz() throws Exception {
        String auth = registerAndLogin("papers@test.com");
        Integer subjectId = JsonPath.read(postJson("/api/subjects", auth, "{\"name\":\"Algorithms\"}"), "$.id");
        Subject subject = subjectRepository.findById(subjectId.longValue()).orElseThrow();
        Topic existingTopic = topicRepository.save(new Topic("Existing topic", null, subject));
        questionRepository.save(new Question(existingTopic, "Older question",
                List.of("A", "B", "C", "D"), 0, "Because", 0.0));
        when(analysisService.analyze(any(), any())).thenReturn(new SubjectAnalysisResponse(
                new MlAnalyzeResponse(2, 1, List.of(new MlCluster(1,
                        List.of("Dynamic programming", "Knapsack"), 2, 1, 1.0,
                        List.of(new MlQuestion(1L, 2025, "Explain the 0/1 knapsack problem.")))),
                        List.of()), LocalDateTime.now()));
        when(mlClient.generateQuestions(any())).thenReturn(new MlGenerateResponse(List.of(
                new MlGeneratedQuestion("Question one", List.of("A", "B", "C", "D"), 0, "Because", 3),
                new MlGeneratedQuestion("Question two", List.of("A", "B", "C", "D"), 0, "Because", 3),
                new MlGeneratedQuestion("Question three", List.of("A", "B", "C", "D"), 0, "Because", 3))));

        String generatedBody = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/subjects/{id}/questions/generate-from-papers", subjectId)
                                .file(new MockMultipartFile("files", "paper.pdf", "application/pdf",
                                        "pdf content".getBytes()))
                                .param("count", "3")
                                .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[0]").value("Dynamic programming, Knapsack"))
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.questions[0].correctIndex").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        List<Integer> questionIds = JsonPath.read(generatedBody, "$.questions[*].id");
        String quizBody = postJson("/api/subjects/{id}/quiz", auth,
                "{\"maxQuestions\":3,\"questionIds\":" + questionIds + "}", subjectId);
        Integer sessionId = JsonPath.read(quizBody, "$.sessionId");
        assertThat(JsonPath.<String>read(quizBody, "$.question.prompt")).isNotEqualTo("Older question");
        assertThat(JsonPath.<Integer>read(quizBody, "$.question.totalQuestions")).isEqualTo(3);

        answer(auth, sessionId, "{\"selectedIndex\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.explanation").value("Because"))
                .andExpect(jsonPath("$.nextQuestion.prompt").value(
                        org.hamcrest.Matchers.not("Older question")))
                .andExpect(jsonPath("$.nextQuestion").exists());
    }

    @Test
    void paperGenerationBatchesTopicsIntoOneMlRequest() throws Exception {
        String auth = registerAndLogin("batched-papers@test.com");
        Integer subjectId = JsonPath.read(postJson("/api/subjects", auth,
                "{\"name\":\"Algorithms\"}"), "$.id");
        when(analysisService.analyze(any(), any())).thenReturn(new SubjectAnalysisResponse(
                new MlAnalyzeResponse(2, 1, List.of(
                        new MlCluster(1, List.of("Dynamic programming"), 1, 1, 1.0,
                                List.of(new MlQuestion(1L, 2025, "Explain dynamic programming."))),
                        new MlCluster(2, List.of("Graph traversal"), 1, 1, 1.0,
                                List.of(new MlQuestion(1L, 2025, "Explain breadth-first search.")))),
                        List.of()), LocalDateTime.now()));
        when(mlClient.generateQuestions(any())).thenReturn(new MlGenerateResponse(List.of(
                new MlGeneratedQuestion("Question on dynamic programming.", List.of("A", "B", "C", "D"),
                        0, "Because", 3, "multiple_choice", null, "Dynamic programming"),
                new MlGeneratedQuestion("Question on graph traversal.", List.of("A", "B", "C", "D"),
                        1, "Because", 3, "multiple_choice", null, "Graph traversal"))));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/subjects/{id}/questions/generate-from-papers", subjectId)
                        .file(new MockMultipartFile("files", "paper.pdf", "application/pdf",
                                "pdf content".getBytes()))
                        .param("count", "2")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics.length()").value(2))
                .andExpect(jsonPath("$.questions.length()").value(2));

        var request = forClass(MlGenerateRequest.class);
        verify(mlClient, times(1)).generateQuestions(request.capture());
        assertThat(request.getValue().topicNames())
                .containsExactly("Dynamic programming", "Graph traversal");
    }

    @Test
    void subjectivePracticeGradesTheAnswerAndReturnsAnInlineWritingEstimate() throws Exception {
        String auth = registerAndLogin("subjective@test.com");
        Integer subjectId = JsonPath.read(
                postJson("/api/subjects", auth, "{\"name\":\"Algorithms\"}"), "$.id");
        when(analysisService.analyze(any(), any())).thenReturn(new SubjectAnalysisResponse(
                new MlAnalyzeResponse(1, 1, List.of(new MlCluster(1,
                        List.of("Dynamic programming"), 1, 1, 1.0,
                        List.of(new MlQuestion(1L, 2025, "Explain dynamic programming.")))),
                        List.of()), LocalDateTime.now()));
        when(mlClient.generateQuestions(any())).thenAnswer(invocation -> {
            MlGenerateRequest request = invocation.getArgument(0);
            if (request.questionType().equals("subjective")) {
                return new MlGenerateResponse(List.of(
                        new MlGeneratedQuestion("Explain dynamic programming.", List.of(), null,
                                "Award credit for subproblems, recurrence, and storing results.", 3,
                                "subjective",
                                "Dynamic programming breaks a problem into overlapping subproblems and stores their results.")));
            }
            return new MlGenerateResponse(List.of(
                    new MlGeneratedQuestion("Which property supports dynamic programming?",
                            List.of("Optimal substructure", "Random ordering", "Unique values", "No recurrence"),
                            0, "It relies on optimal substructure.", 3)));
        });

        String generatedBody = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/subjects/{id}/questions/generate-from-papers", subjectId)
                                .file(new MockMultipartFile("files", "paper.pdf", "application/pdf",
                                        "pdf content".getBytes()))
                                .param("count", "2")
                                .param("format", "mixed")
                                .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].questionType").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.questions[1].questionType").value("SUBJECTIVE"))
                .andExpect(jsonPath("$.questions[1].options").isEmpty())
                .andReturn().getResponse().getContentAsString();
        List<Integer> questionIds = JsonPath.read(generatedBody, "$.questions[*].id");
        String quizBody = postJson("/api/subjects/{id}/quiz", auth,
                "{\"maxQuestions\":1,\"questionIds\":[" + questionIds.get(1) + "]}", subjectId);
        Integer sessionId = JsonPath.read(quizBody, "$.sessionId");

        when(mlClient.detect(any())).thenReturn(
                new MlDetectResponse(true, 0.82, 0.18, 0.4, "Some patterns resemble AI writing."));
        when(mlClient.gradeAnswer(any())).thenReturn(
                new MlGradeAnswerResponse(0.85, "Good explanation of the central idea."));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/quiz/{id}/subjective-answer", sessionId)
                        .param("answerText", "Dynamic programming solves a task by dividing it into smaller overlapping subproblems and storing each result for reuse.")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.score").value(0.85))
                .andExpect(jsonPath("$.aiWritingAssessment.aiProbability").value(0.82))
                .andExpect(jsonPath("$.finished").value(true));
    }

    @Test
    void interviewTurnRequiresAuthenticationAndReturnsNextQuestion() throws Exception {
        String auth = registerAndLogin("interview@test.com");
        when(mlClient.interviewTurn(any())).thenReturn(new MlInterviewTurnResponse(
                "How would you handle a cache invalidation problem?", "", 0.0, false));

        mockMvc.perform(post("/api/interview/turn")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"engineering_role","target":"Backend Engineer",
                                 "level":"intermediate","questionNumber":0,"questionCount":5,
                                 "history":[],"answer":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value(
                        "How would you handle a cache invalidation problem?"))
                .andExpect(jsonPath("$.complete").value(false));
    }

    @Test
    void authenticatedInterviewAudioCanBeTranscribed() throws Exception {
        String auth = registerAndLogin("transcription@test.com");
        when(mlClient.transcribeAudio(any(byte[].class), anyString(), anyString()))
                .thenReturn(new MlTranscriptionResponse("A spoken answer.", "en"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/interview/transcribe")
                        .file(new MockMultipartFile("file", "answer.webm", "audio/webm",
                                "audio bytes".getBytes()))
                        .param("language", "en-US")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("A spoken answer."))
                .andExpect(jsonPath("$.language").value("en"));

        verify(mlClient).transcribeAudio(any(byte[].class),
                org.mockito.ArgumentMatchers.eq("answer.webm"),
                org.mockito.ArgumentMatchers.eq("en-US"));

        mockMvc.perform(post("/api/interview/turn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"engineering_role","target":"Backend Engineer",
                                 "level":"intermediate","questionNumber":0,"questionCount":5,
                                 "history":[],"answer":null}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void anotherUserCannotReadMySubject() throws Exception {
        String ownerAuth = registerAndLogin("owner@test.com");
        String intruderAuth = registerAndLogin("intruder@test.com");
        Integer subjectId = JsonPath.read(
                postJson("/api/subjects", ownerAuth, "{\"name\":\"Private\"}"), "$.id");

        mockMvc.perform(get("/api/subjects/{id}", subjectId).header("Authorization", intruderAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Not your subject"));
    }

    @Test
    void requestsWithoutATokenAreRejected() throws Exception {
        mockMvc.perform(get("/api/subjects")).andExpect(status().is4xxClientError());
    }

    @Test
    void registeringTheSameEmailTwiceFails() throws Exception {
        String body = "{\"email\":\"dup@test.com\",\"password\":\"pass123\",\"fullName\":\"Dup\"}";
        postJson("/api/auth/register", null, body);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}