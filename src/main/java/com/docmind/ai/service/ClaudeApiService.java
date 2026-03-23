package com.docmind.ai.service;

import com.docmind.global.exception.BusinessException;
import com.docmind.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Claude API 서비스.
 *
 * Anthropic Messages API를 WebClient/RestClient로 직접 호출한다.
 * 스트리밍: SSE text/event-stream으로 수신하여 SseEmitter에 전달.
 *
 * 모델: claude-sonnet-4-20250514
 * max_tokens: 4096
 */
@Service
public class ClaudeApiService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeApiService.class);

    private final RestClient claudeClient;
    private final String model;
    private final int maxTokens;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ClaudeApiService(@Value("${claude.api-key}") String apiKey,
                            @Value("${claude.model:claude-sonnet-4-20250514}") String model,
                            @Value("${claude.max-tokens:4096}") int maxTokens) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.claudeClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 요약 생성 (SSE 스트리밍).
     */
    public SseEmitter streamSummary(String documentText) {
        String systemPrompt = """
                당신은 문서 요약 전문가입니다.
                주어진 문서를 한국어로 3~5문단으로 요약하세요.
                핵심 내용, 주요 결론, 중요한 수치/사실을 포함하세요.
                """;
        return streamChat(systemPrompt, documentText);
    }

    /**
     * Claude API 스트리밍 호출.
     * SseEmitter를 반환하여 컨트롤러에서 바로 SSE 응답으로 사용한다.
     *
     * @param systemPrompt 시스템 프롬프트
     * @param userMessage  사용자 메시지
     * @return SseEmitter (SSE 스트리밍)
     */
    public SseEmitter streamChat(String systemPrompt, String userMessage) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2분 타임아웃

        executor.execute(() -> {
            try {
                streamChatInternal(systemPrompt, userMessage,
                        text -> {
                            try {
                                emitter.send(text);
                            } catch (Exception e) {
                                log.warn("Failed to send SSE event", e);
                            }
                        },
                        () -> emitter.complete(),
                        error -> {
                            log.error("Claude streaming error", error);
                            emitter.completeWithError(error);
                        }
                );
            } catch (Exception e) {
                log.error("Claude API streaming failed", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 스트리밍 호출 내부 구현.
     * 콜백 기반: onText, onComplete, onError.
     */
    public void streamChatInternal(String systemPrompt, String userMessage,
                                    Consumer<String> onText,
                                    Runnable onComplete,
                                    Consumer<Throwable> onError) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "stream", true,
                    "system", systemPrompt,
                    "messages", List.of(
                            Map.of("role", "user", "content", userMessage)
                    )
            );

            // RestClient로 스트리밍 수신 (InputStream 직접 읽기)
            InputStream responseStream = claudeClient.post()
                    .uri("/messages")
                    .body(requestBody)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                                    "Claude API returned " + response.getStatusCode());
                        }
                        return response.getBody();
                    });

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        // content_block_delta 이벤트에서 텍스트 추출
                        String extractedText = extractTextDelta(data);
                        if (extractedText != null && !extractedText.isEmpty()) {
                            onText.accept(extractedText);
                        }
                    }
                }
            }

            onComplete.run();

        } catch (BusinessException e) {
            onError.accept(e);
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            onError.accept(new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                    "Claude API call failed: " + e.getMessage()));
        }
    }

    /**
     * 스트리밍이 아닌 동기 호출 (요약 저장용).
     */
    public String chat(String systemPrompt, String userMessage) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "system", systemPrompt,
                    "messages", List.of(
                            Map.of("role", "user", "content", userMessage)
                    )
            );

            ClaudeResponse response = claudeClient.post()
                    .uri("/messages")
                    .body(requestBody)
                    .retrieve()
                    .body(ClaudeResponse.class);

            if (response == null || response.content() == null || response.content().isEmpty()) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Empty response from Claude API");
            }

            return response.content().stream()
                    .filter(block -> "text".equals(block.type()))
                    .map(ContentBlock::text)
                    .reduce("", String::concat);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR,
                    "Claude API call failed: " + e.getMessage());
        }
    }

    public String getModel() {
        return model;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * SSE 이벤트 데이터에서 content_block_delta의 text를 추출한다.
     * JSON 파싱 간소화: "text":" 패턴으로 빠르게 추출.
     */
    private String extractTextDelta(String jsonData) {
        // content_block_delta 이벤트만 처리
        if (!jsonData.contains("content_block_delta")) {
            return null;
        }
        // delta.text 추출 - "text":"..." 패턴
        int textKeyIdx = jsonData.lastIndexOf("\"text\":\"");
        if (textKeyIdx < 0) {
            return null;
        }
        int start = textKeyIdx + 8; // "text":" 길이
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < jsonData.length(); i++) {
            char c = jsonData.charAt(i);
            if (c == '"' && (i == start || jsonData.charAt(i - 1) != '\\')) {
                break;
            }
            if (c == '\\' && i + 1 < jsonData.length()) {
                char next = jsonData.charAt(i + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Claude API response DTOs
    // -------------------------------------------------------------------------

    record ClaudeResponse(
            @JsonProperty("id") String id,
            @JsonProperty("content") List<ContentBlock> content,
            @JsonProperty("model") String model,
            @JsonProperty("stop_reason") String stopReason,
            @JsonProperty("usage") ClaudeUsage usage
    ) {}

    record ContentBlock(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text
    ) {}

    record ClaudeUsage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens
    ) {}
}
