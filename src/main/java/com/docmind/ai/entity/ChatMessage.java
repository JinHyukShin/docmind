package com.docmind.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * chat_message 테이블 매핑 엔티티.
 *
 * 인덱스 전략 (DDL 기반):
 *   - idx_chat_message_session : (chat_session_id, created_at) → 세션별 메시지 시간순 페이징
 *
 * sourceChunkIds: AI 응답이 참조한 document_chunk.id 배열.
 *   PostgreSQL BIGINT[] 타입을 Long[]로 매핑.
 *   columnDefinition = "BIGINT[]" 명시.
 *
 * @ManyToOne fetch=LAZY 필수: 메시지 목록 조회 시 ChatSession 전체 로딩 방지.
 */
@Entity
@Table(
    name = "chat_message",
    indexes = {
        @Index(name = "idx_chat_message_session", columnList = "chat_session_id, created_at")
    }
)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY 필수: 메시지 목록 조회 시 ChatSession 전체를 즉시 로딩하면
     * 세션 정보가 중복 로딩됨.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession chatSession;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * AI 응답이 참조한 청크 ID 배열.
     * USER 역할 메시지에서는 null.
     * columnDefinition = "BIGINT[]": PostgreSQL 배열 타입 명시.
     */
    @Column(name = "source_chunk_ids", columnDefinition = "BIGINT[]")
    private Long[] sourceChunkIds;

    @Column(length = 50)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {
        // JPA 전용
    }

    private ChatMessage(ChatSession chatSession, String role, String content) {
        this.chatSession = chatSession;
        this.role        = role;
        this.content     = content;
        this.createdAt   = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    /** 사용자 질문 메시지 생성 */
    public static ChatMessage createUserMessage(ChatSession chatSession, String content) {
        return new ChatMessage(chatSession, "USER", content);
    }

    /** AI 응답 메시지 생성 (토큰 수, 참조 청크 포함) */
    public static ChatMessage createAssistantMessage(ChatSession chatSession, String content,
                                                      String model, Integer inputTokens,
                                                      Integer outputTokens, Long[] sourceChunkIds) {
        ChatMessage message = new ChatMessage(chatSession, "ASSISTANT", content);
        message.model          = model;
        message.inputTokens    = inputTokens;
        message.outputTokens   = outputTokens;
        message.sourceChunkIds = sourceChunkIds;
        return message;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public ChatSession getChatSession() {
        return chatSession;
    }

    public Long getChatSessionId() {
        return chatSession.getId();
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Long[] getSourceChunkIds() {
        return sourceChunkIds;
    }

    public String getModel() {
        return model;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
