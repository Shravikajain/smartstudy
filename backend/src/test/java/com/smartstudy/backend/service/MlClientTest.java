package com.smartstudy.backend.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.smartstudy.backend.dto.ml.MlGradeAnswerRequest;
import com.smartstudy.backend.exception.MlServiceUnavailableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MlClientTest {

    private static final MlGradeAnswerRequest GRADE =
            new MlGradeAnswerRequest("Explain Kruskal's algorithm.", "Model answer", "Rubric", "My answer");

    @Test
    void fastApiErrorDetailIsShownInsteadOfRawJson() {
        assertThat(MlClient.detail("{\"detail\":\"The AI-writing detector is still loading.\"}", "fallback"))
                .isEqualTo("The AI-writing detector is still loading.");
        assertThat(MlClient.detail("{\"detail\":\"Say \\\"hi\\\"\"}", "fallback")).isEqualTo("Say \"hi\"");
    }

    @Test
    void validationErrorsAndEmptyBodiesUseTheFallbackMessage() {
        assertThat(MlClient.detail("{\"detail\":[{\"loc\":[\"body\"]}]}", "fallback")).isEqualTo("fallback");
        assertThat(MlClient.detail("", "fallback")).isEqualTo("fallback");
        assertThat(MlClient.detail(null, "fallback")).isEqualTo("fallback");
    }

    // Grading right after a failed AI-writing check got "Connection reset" while reading the
    // response, and the learner's answer failed with a 500. ML calls have no side effects, so one
    // retry on a fresh connection is safe.
    @Test
    void requestIsRetriedOnceWhenTheConnectionIsReset() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            serve(server, false);
            MlClient client = clientFor(server);

            assertThat(client.gradeAnswer(GRADE).score()).isEqualTo(0.9);
        }
    }

    @Test
    void repeatedResetIsReportedAsTheMlServiceBeingUnavailable() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            serve(server, true);
            MlClient client = clientFor(server);

            assertThatThrownBy(() -> client.gradeAnswer(GRADE))
                    .isInstanceOf(MlServiceUnavailableException.class)
                    .hasMessageContaining("try again");
        }
    }

    private static MlClient clientFor(ServerSocket server) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient rest = RestClient.builder()
                .baseUrl("http://localhost:" + server.getLocalPort())
                .requestFactory(factory)
                .build();
        return new MlClient(rest, rest);
    }

    // First connection: read the request, then reset it without answering. Second connection:
    // reset again, or answer with a grade.
    private static void serve(ServerSocket server, boolean resetBoth) {
        Thread ml = new Thread(() -> {
            try {
                reset(server.accept());
                Socket second = server.accept();
                if (resetBoth) {
                    reset(second);
                    return;
                }
                try (second) {
                    readRequest(second);
                    String body = "{\"score\":0.9,\"feedback\":\"Good\"}";
                    OutputStream out = second.getOutputStream();
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                            + body.length() + "\r\n\r\n" + body).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (Exception ignored) {
                // the test's assertions report the failure
            }
        });
        ml.setDaemon(true);
        ml.start();
    }

    private static void reset(Socket socket) throws Exception {
        readRequest(socket);
        socket.setSoLinger(true, 0);                    // close with RST instead of FIN
        socket.close();
    }

    private static void readRequest(Socket socket) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        int length = 0;
        boolean chunked = false;
        for (String line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
            String header = line.toLowerCase();
            if (header.startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(15).trim());
            }
            chunked |= header.startsWith("transfer-encoding:") && header.contains("chunked");
        }
        // Read the whole body: closing a socket with unread input resets it before our reply arrives.
        if (!chunked) {
            skip(reader, length);
            return;
        }
        for (int size = Integer.parseInt(reader.readLine().trim(), 16); size > 0;
             size = Integer.parseInt(reader.readLine().trim(), 16)) {
            skip(reader, size);
            reader.readLine();                          // CRLF after the chunk
        }
        reader.readLine();                              // blank line after the last chunk
    }

    private static void skip(BufferedReader reader, int length) throws Exception {
        char[] body = new char[length];
        for (int read = 0; read < length; ) {
            read += reader.read(body, read, length - read);
        }
    }
}
