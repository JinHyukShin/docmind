package com.docmind.ai.entity;

import com.docmind.document.entity.Document;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * chat_session_document 테이블 매핑 엔티티 (세션-문서 연결).
 *
 * (chat_session_id, document_id) UNIQUE 제약: 중복 연결 방지.
 * UNIQUE 제약이 복합 인덱스를 생성하므로 별도 인덱스 불필요.
 *
 * 멀티 문서 Q&A: 하나의 채팅 세션이 여러 문서를 참조할 수 있다.
 *
 * @ManyToOne fetch=LAZY 필수: 세션-문서 목록 조회 시 전체 엔티티 로딩 방지.
 */
@Entity
@Table(
    name = "chat_session_document",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_chat_session_document",
        columnNames = {"chat_session_id", "document_id"}
    )
)
public class ChatSessionDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession chatSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    protected ChatSessionDocument() {
        // JPA 전용
    }

    private ChatSessionDocument(ChatSession chatSession, Document document) {
        this.chatSession = chatSession;
        this.document    = document;
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    public static ChatSessionDocument create(ChatSession chatSession, Document document) {
        return new ChatSessionDocument(chatSession, document);
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

    public Document getDocument() {
        return document;
    }

    public Long getDocumentId() {
        return document.getId();
    }
}
