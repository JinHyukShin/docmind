package com.docmind.ai.entity;

import com.docmind.document.entity.Document;
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
 * document_chunk 테이블 매핑 엔티티.
 *
 * 인덱스 전략 (DDL 기반):
 *   - idx_chunk_document_id : document_id → 청크 목록 조회
 *   - idx_chunk_chunk_index : (document_id, chunk_index) → 순서 보장 조회
 *   - idx_chunk_embedding   : HNSW (vector_cosine_ops) → pgvector ANN 검색
 *   - idx_chunk_content_tsv : GIN (tsvector) → 전문검색
 *
 * embedding 컬럼:
 *   Hibernate 6에서 PostgreSQL vector 타입은 기본 지원이 없다.
 *   columnDefinition = "vector(1024)"로 DDL 타입을 명시하고,
 *   float[] 로 Java 타입 매핑한다.
 *   pgvector-hibernate 라이브러리(io.github.pgvector:pgvector-hibernate)를
 *   사용하면 더 세밀한 타입 지원이 가능하나, 표준 방식으로 구현.
 *
 * content_tsv 컬럼:
 *   DB에서 GENERATED ALWAYS AS ... STORED로 관리되므로 Java에서는
 *   insertable=false, updatable=false로 설정하여 읽기 전용 매핑.
 *
 * @ManyToOne fetch=LAZY 필수: 청크 검색 시 Document 전체 로딩 방지.
 */
@Entity
@Table(
    name = "document_chunk",
    indexes = {
        @Index(name = "idx_chunk_document_id", columnList = "document_id"),
        @Index(name = "idx_chunk_chunk_index", columnList = "document_id, chunk_index")
        // idx_chunk_embedding (HNSW) 및 idx_chunk_content_tsv (GIN)은
        // DDL에서만 생성 가능, JPA @Index로 표현 불가
    }
)
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY 필수: 벡터 유사도 검색 결과 목록에서 Document 전체 로딩 시
     * N+1 쿼리 발생 → 검색 레이어 성능 저하.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    /**
     * pgvector embedding 컬럼.
     * columnDefinition = "vector(1024)": Hibernate DDL 생성 시 올바른 타입 지정.
     * float[]: Java 측 표현, pgvector JDBC 드라이버가 변환을 처리한다.
     */
    @Column(columnDefinition = "vector(1024)")
    private float[] embedding;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    /**
     * content_tsv: DB에서 GENERATED ALWAYS AS STORED로 관리.
     * insertable=false, updatable=false: Java에서 쓰기 금지, 읽기 전용.
     */
    @Column(name = "content_tsv", columnDefinition = "tsvector",
            insertable = false, updatable = false)
    private String contentTsv;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentChunk() {
        // JPA 전용
    }

    private DocumentChunk(Document document, int chunkIndex, String content,
                           int tokenCount, float[] embedding, Integer pageStart, Integer pageEnd) {
        this.document   = document;
        this.chunkIndex = chunkIndex;
        this.content    = content;
        this.tokenCount = tokenCount;
        this.embedding  = embedding;
        this.pageStart  = pageStart;
        this.pageEnd    = pageEnd;
        this.createdAt  = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    public static DocumentChunk create(Document document, int chunkIndex, String content,
                                       int tokenCount, float[] embedding,
                                       Integer pageStart, Integer pageEnd) {
        return new DocumentChunk(document, chunkIndex, content, tokenCount,
                                 embedding, pageStart, pageEnd);
    }

    public static DocumentChunk createWithoutEmbedding(Document document, int chunkIndex,
                                                        String content, int tokenCount,
                                                        Integer pageStart, Integer pageEnd) {
        return new DocumentChunk(document, chunkIndex, content, tokenCount,
                                 null, pageStart, pageEnd);
    }

    // -------------------------------------------------------------------------
    // Domain methods
    // -------------------------------------------------------------------------

    /** 임베딩 벡터 설정 (EMBEDDING 단계에서 호출) */
    public void assignEmbedding(float[] embedding) {
        this.embedding = embedding;
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

    /** documentId만 필요할 때 LAZY 프록시 초기화 없이 FK 값 직접 접근 */
    public Long getDocumentId() {
        return document.getId();
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public Integer getPageStart() {
        return pageStart;
    }

    public Integer getPageEnd() {
        return pageEnd;
    }

    public String getContentTsv() {
        return contentTsv;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
