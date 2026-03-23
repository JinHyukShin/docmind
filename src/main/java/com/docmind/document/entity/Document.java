package com.docmind.document.entity;

import com.docmind.auth.entity.User;
import com.docmind.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * document 테이블 매핑 엔티티.
 *
 * 인덱스 전략 (DDL 기반):
 *   - idx_document_user_id    : user_id → 사용자 문서 목록 조회 (가장 빈번)
 *   - idx_document_status     : status → 파이프라인 워커 처리 대기 문서 조회
 *   - idx_document_created_at : created_at DESC → 최신순 목록
 *
 * @ManyToOne fetch=LAZY 필수: 문서 목록 조회 시 User 전체 로딩 방지.
 * setter 미사용: 상태 변경은 updateStatus() 도메인 메서드로만 허용.
 */
@Entity
@Table(
    name = "document",
    indexes = {
        @Index(name = "idx_document_user_id",    columnList = "user_id"),
        @Index(name = "idx_document_status",     columnList = "status"),
        @Index(name = "idx_document_created_at", columnList = "created_at DESC")
    }
)
public class Document extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY 필수: 문서 목록 조회 시 User 전체를 즉시 로딩하면
     * N+1 문제 발생 → 성능 저하.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "original_file_name", nullable = false, length = 500)
    private String originalFileName;

    @Column(name = "stored_file_path", nullable = false, length = 1000)
    private String storedFilePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "text_length")
    private Integer textLength;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected Document() {
        // JPA 전용
    }

    private Document(User user, String title, String originalFileName,
                     String storedFilePath, long fileSize, String mimeType) {
        this.user             = user;
        this.title            = title;
        this.originalFileName = originalFileName;
        this.storedFilePath   = storedFilePath;
        this.fileSize         = fileSize;
        this.mimeType         = mimeType;
        this.status           = DocumentStatus.UPLOADED;
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    public static Document create(User user, String title, String originalFileName,
                                  String storedFilePath, long fileSize, String mimeType) {
        return new Document(user, title, originalFileName, storedFilePath, fileSize, mimeType);
    }

    // -------------------------------------------------------------------------
    // Domain methods
    // -------------------------------------------------------------------------

    /**
     * 파이프라인 상태 전이.
     * 실패 시 에러 메시지를 함께 기록한다.
     */
    public void updateStatus(DocumentStatus newStatus) {
        this.status       = newStatus;
        this.errorMessage = null;
    }

    /**
     * 처리 실패 상태로 전이하면서 에러 메시지를 기록한다.
     */
    public void markFailed(String errorMessage) {
        this.status       = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    /**
     * 파싱 완료 후 메타데이터를 갱신한다.
     */
    public void completeParsing(int pageCount, int textLength) {
        this.pageCount   = pageCount;
        this.textLength  = textLength;
        this.status      = DocumentStatus.CHUNKING;
    }

    /**
     * 임베딩 완료 후 청크 수를 갱신하고 READY 상태로 전이한다.
     */
    public void completeEmbedding(int chunkCount) {
        this.chunkCount = chunkCount;
        this.status     = DocumentStatus.READY;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    /** userId만 필요할 때 LAZY 프록시 초기화 없이 FK 값 직접 접근 */
    public Long getUserId() {
        return user.getId();
    }

    public String getTitle() {
        return title;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getStoredFilePath() {
        return storedFilePath;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Integer getTextLength() {
        return textLength;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
