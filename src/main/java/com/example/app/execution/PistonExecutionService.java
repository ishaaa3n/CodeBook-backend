package com.example.app.execution;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs user code via the free public Piston API (emkc.org) instead of a local
 * Docker daemon, so the app can run on hosts without docker.sock access.
 */
@Component
@Slf4j
public class PistonExecutionService {

    private static final String PISTON_URL = "https://emkc.org/api/v2/piston/execute";
    private static final int TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExecutionResult execute(String language, String code, String input, Long executionId) {
        long startTime = System.currentTimeMillis();
        try {
            PistonRuntime runtime = resolveRuntime(language);

            Map<String, Object> body = Map.of(
                    "language", runtime.language(),
                    "version", "*",
                    "files", List.of(Map.of(
                            "name", runtime.fileName(),
                            "content", code
                    )),
                    "stdin", input == null ? "" : input,
                    "run_timeout", TIMEOUT_SECONDS * 1000
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PISTON_URL))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS + 5))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long executionTime = System.currentTimeMillis() - startTime;

            if (response.statusCode() != 200) {
                return ExecutionResult.builder()
                        .status(ExecutionStatus.SYSTEM_ERROR)
                        .error("Piston API error: HTTP " + response.statusCode() + " - " + response.body())
                        .executionTime(executionTime)
                        .build();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode compile = root.path("compile");
            JsonNode run = root.path("run");

            String output = run.path("stdout").asText("");
            String error = run.path("stderr").asText("");

            boolean compileFailed = !compile.isMissingNode() && compile.path("code").asInt(0) != 0;
            if (compileFailed) {
                error = compile.path("stderr").asText(error);
            }

            int exitCode = run.path("code").asInt(-1);
            ExecutionStatus status = (!compileFailed && exitCode == 0)
                    ? ExecutionStatus.SUCCESS
                    : ExecutionStatus.FAILED;

            return ExecutionResult.builder()
                    .output(output)
                    .error(error)
                    .status(status)
                    .executionTime(executionTime)
                    .build();

        } catch (Exception e) {
            log.error("Piston execution failed for executionId={}", executionId, e);
            return ExecutionResult.builder()
                    .status(ExecutionStatus.SYSTEM_ERROR)
                    .error(e.getMessage())
                    .executionTime(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private PistonRuntime resolveRuntime(String language) {
        return switch (language.toLowerCase()) {
            case "python" -> new PistonRuntime("python", "main.py");
            case "javascript" -> new PistonRuntime("javascript", "main.js");
            case "java" -> new PistonRuntime("java", "Main.java");
            case "cpp", "c++" -> new PistonRuntime("c++", "main.cpp");
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    private record PistonRuntime(String language, String fileName) {}
}
