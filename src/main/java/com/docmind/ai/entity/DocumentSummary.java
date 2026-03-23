package com.docmind.ai.entity;

import com.docmind.document.entity.Document;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * document_summary 테이블 매핑 엔티티.
 *
 * document_id UNIQUE: 문서당 요약 1건 보장.
 * UNIQUE 제약이 인덱스를 생성하므로 별도 인덱스 불필요.
 *
 * @OneToOne fetch=LAZY 필수: 문서 요약은 항상 명시적으로 로딩해야 함.
 * setter 미사용: 요약 갱신은 update() 도메인 메서드로만 허용.
 */
@Entity
@Table(name = "document_summary")
public class DocumentSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY 필수: 요약 조회 시 Document 전체를 즉시 로딩하면
     * 불필요한 JOIN 발생.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    private Document document;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentSummary() {
        // JPA 전용
    }

    private DocumentSummary(Document document, String content, String model,
                             Integer inputTokens, Integer outputTokens) {
        this.document     = document;
        this.content      = content;
        this.model        = model;
        this.inputTokens  = inputTokens;
        this.outputTokens = outputTokens;
        this.createdAt    = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    public static DocumentSummary create(Document document, String content, String model,
                                         Integer inputTokens, Integer outputTokens) {
        return new DocumentSummary(document, content, model, inputTokens, outputTokens);
    }

    // -------------------------------------------------------------------------
    // Domain methods
    // -------------------------------------------------------------------------

    /** 요약 재생성 시 내용과 토큰 수 갱신 */
    public void update(String newContent, String model, Integer inputTokens, Integer outputTokens) {
        this.content      = newContent;
        this.model        = model;
        this.inputTokens  = inputTokens;
        this.outputTokens = outputTokens;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public Long getDocumentId() {
        return document.getId();
    }

    public String getContent() {
        return content;
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
