package com.docmind.ai.service;

import com.docmind.ai.entity.ChatMessage;
import com.docmind.ai.entity.ChatSession;
import com.docmind.ai.entity.DocumentChunk;
import com.docmind.ai.repository.ChatMessageRepository;
import com.docmind.ai.repository.ChatSessionDocumentRepository;
import com.docmind.ai.repository.DocumentChunkRepository;
import com.docmind.global.exception.BusinessException;
import com.docmind.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * RAG Q&A 서비스.
 *
 * 파이프라인:
 *   1. 질문 임베딩 (EmbeddingService)
 *   2. pgvector Top-K 검색 (k=5)
 *   3. 컨텍스트 조립 (검색된 청크)
 *   4. 시스템 프롬프트 + 컨텍스트 + 질문 -> Claude API
 *   5. SSE 스트리밍 응답
 *   6. 완료 시 ChatMessage 저장
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int TOP_K = 5;

    private static final String SYSTEM_PROMPT = """
            당신은 문서 기반 Q&A 어시스턴트입니다.
            아래 제공된 문서 컨텍스트를 기반으로 사용자의 질문에 답변하세요.

            규칙:
            - 컨텍스트에 있는 정보만 사용하여 답변하세요.
            - 컨텍스트에 답이 없으면 "제공된 문서에서 관련 정보를 찾을 수 없습니다"라고 답하세요.
            - 답변 시 참조한 문서와 청크 번호를 명시하세요.
            - 한국어로 답변하세요.
            """;

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository chunkRepository;
    private final ChatSessionDocumentRepository sessionDocumentRepository;
    private final ChatMessageRepository messageRepository;
    private final ClaudeApiService claudeApiService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public RagService(EmbeddingService embeddingService,
                      DocumentChunkRepository chunkRepository,
                      ChatSessionDocumentRepository sessionDocumentRepository,
                      ChatMessageRepository messageRepository,
                      ClaudeApiService claudeApiService) {
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
        this.sessionDocumentRepository = sessionDocumentRepository;
        this.messageRepository = messageRepository;
        this.claudeApiService = claudeApiService;
    }

    /**
     * RAG Q&A: 질문 -> 임베딩 -> 검색 -> 컨텍스트 조립 -> Claude -> SSE 스트리밍.
     *
     * @return SseEmitter (sources + answer 이벤트 스트리밍)
     */
    public SseEmitter askQuestion(ChatSession session, String question, Long userId) {
        Long sessionId = session.getId();

        // 세션에 연결된 문서 ID 목록 조회
        List<Long> documentIds = sessionDocumentRepository.findDocumentIdsByChatSessionId(sessionId);
        if (documentIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "No documents linked to this session");
        }

        // 1. 질문 임베딩
        float[] questionEmbedding = embeddingService.embedQuery(question);
        String queryVector = arrayToString(questionEmbedding);

        // 2. pgvector Top-K 검색
        List<DocumentChunk> relevantChunks = chunkRepository.findTopKBySimilarity(
                queryVector, userId, documentIds, TOP_K);

        // 3. 컨텍스트 조립
        String context = relevantChunks.stream()
                .map(chunk -> String.format("[문서: %s, 청크 #%d]\n%s",
                        chunk.getDocument().getTitle(),
                        chunk.getChunkIndex(),
                        chunk.getContent()))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 소스 청크 정보 (SSE sources 이벤트용)
        List<SourceChunk> sources = relevantChunks.stream()
                .map(chunk -> new SourceChunk(
                        chunk.getId(),
                        chunk.getDocument().getTitle(),
                        chunk.getContent().length() > 200
                                ? chunk.getContent().substring(0, 200) + "..."
                                : chunk.getContent(),
                        0.0 // score는 nativeQuery에서 별도 매핑 필요, 임시 0
                ))
                .toList();

        // 4. 사용자 메시지 저장
        ChatMessage userMessage = ChatMessage.createUserMessage(session, question);
        messageRepository.save(userMessage);

        // 5. 시스템 프롬프트 + 컨텍스트 + 질문 조립
        String userPrompt = String.format("""
                [컨텍스트]
                %s

                [질문]
                %s
                """, context, question);

        // 6. SSE 스트리밍
        SseEmitter emitter = new SseEmitter(120_000L);
        StringBuilder fullResponse = new StringBuilder();

        executor.execute(() -> {
            try {
                // sources 이벤트 전송
                emitter.send(SseEmitter.event()
                        .name("sources")
                        .data(new SourcesEvent(sources)));

                // Claude 스트리밍 호출
                claudeApiService.streamChatInternal(
                        SYSTEM_PROMPT,
                        userPrompt,
                        // onText
                        text -> {
                            fullResponse.append(text);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("answer")
                                        .data(new AnswerEvent(text, false, null)));
                            } catch (Exception e) {
                                log.warn("Failed to send answer SSE event", e);
                            }
                        },
                        // onComplete
                        () -> {
                            try {
                                // AI 메시지 저장
                                Long[] chunkIds = relevantChunks.stream()
                                        .map(DocumentChunk::getId)
                                        .toArray(Long[]::new);
                                ChatMessage aiMessage = ChatMessage.createAssistantMessage(
                                        session, fullResponse.toString(),
                                        claudeApiService.getModel(), null, null, chunkIds);
                                ChatMessage savedMessage = messageRepository.save(aiMessage);

                                // 완료 이벤트
                                emitter.send(SseEmitter.event()
                                        .name("answer")
                                        .data(new AnswerEvent("", true, savedMessage.getId())));
                                emitter.complete();
                            } catch (Exception e) {
                                log.error("Failed to save AI message", e);
                                emitter.completeWithError(e);
                            }
                        },
                        // onError
                        error -> {
                            log.error("RAG streaming error", error);
                            emitter.completeWithError(error);
                        }
                );
            } catch (Exception e) {
                log.error("RAG pipeline failed", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * float[] -> "[v1, v2, ...]" 문자열 변환 (pgvector 파라미터 형식).
     */
    private String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // SSE event DTOs
    // -------------------------------------------------------------------------

    record SourceChunk(Long chunkId, String documentTitle, String content, double score) {}
    record SourcesEvent(List<SourceChunk> chunks) {}
    record AnswerEvent(String content, boolean done, Long messageId) {}
}
