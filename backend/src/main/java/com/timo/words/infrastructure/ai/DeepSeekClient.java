package com.timo.words.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class DeepSeekClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model}")
    private String model;

    @Value("${deepseek.max-tokens}")
    private int maxTokens;

    @Value("${deepseek.temperature}")
    private double temperature;

    // Circuit breaker state
    private final ReentrantLock circuitLock = new ReentrantLock();
    private int failureCount = 0;
    private static final int FAILURE_THRESHOLD = 3;
    private static final long RESET_WINDOW_MS = 60_000;
    private long lastFailureTime = 0;
    private boolean circuitOpen = false;

    public DeepSeekClient(@Qualifier("aiRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Chat with JSON mode enabled.
     */
    public String chat(List<Map<String, String>> messages) {
        return doRequest(messages, true);
    }

    /**
     * Chat without JSON mode (free-form text responses).
     */
    public String chatFreeForm(List<Map<String, String>> messages) {
        return doRequest(messages, false);
    }

    private String doRequest(List<Map<String, String>> messages, boolean jsonMode) {
        circuitLock.lock();
        try {
            if (circuitOpen) {
                if (System.currentTimeMillis() - lastFailureTime > RESET_WINDOW_MS) {
                    circuitOpen = false;
                    failureCount = 0;
                    log.info("Circuit breaker reset, attempting probe request");
                } else {
                    log.warn("Circuit breaker open, skipping request");
                    return null;
                }
            }
        } finally {
            circuitLock.unlock();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);
            if (jsonMode) {
                body.put("response_format", Map.of("type", "json_object"));
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    baseUrl + "/v1/chat/completions",
                    HttpMethod.POST,
                    request,
                    byte[].class
            );

            byte[] responseBytes = response.getBody();
            if (responseBytes == null || responseBytes.length == 0) {
                throw new java.io.IOException("DeepSeek API returned empty response body");
            }
            String responseBody = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("choices").get(0).path("message");
            String content = message.path("content").asText("");
            // deepseek-v4-flash defaults to thinking mode; actual reply may be in reasoning_content
            if (content.isBlank()) {
                content = message.path("reasoning_content").asText("");
            }

            circuitLock.lock();
            try {
                failureCount = 0;
                circuitOpen = false;
            } finally {
                circuitLock.unlock();
            }

            return content;
        } catch (Exception e) {
            circuitLock.lock();
            try {
                failureCount++;
                lastFailureTime = System.currentTimeMillis();
                log.error("DeepSeek API call failed (failure {}): {}", failureCount, e.getMessage());
                if (failureCount >= FAILURE_THRESHOLD) {
                    circuitOpen = true;
                    log.warn("Circuit breaker opened after {} failures", failureCount);
                }
            } finally {
                circuitLock.unlock();
            }
            return null;
        }
    }

    public boolean isCircuitOpen() {
        return circuitOpen;
    }
}
