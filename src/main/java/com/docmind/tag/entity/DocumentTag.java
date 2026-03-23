package com.docmind.tag.entity;

import com.docmind.document.entity.Document;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * document_tag 테이블 매핑 엔티티 (문서-태그 N:M 연결).
 *
 * (document_id, tag_id) UNIQUE 제약: 중복 태그 방지.
 * UNIQUE 제약이 복합 인덱스를 생성하므로 별도 복합 인덱스 불필요.
 * idx_document_tag_document, idx_document_tag_tag:
 *   각각 단방향 조회(문서→태그, 태그→문서)를 위한 인덱스.
 *
 * @ManyToOne fetch=LAZY 필수: 문서별 태그 목록 조회 시 전체 엔티티 로딩 방지.
 */
@Entity
@Table(
    name = "document_tag",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_document_tag",
        columnNames = {"document_id", "tag_id"}
    ),
    indexes = {
        @Index(name = "idx_document_tag_document", columnList = "document_id"),
        @Index(name = "idx_document_tag_tag",      columnList = "tag_id")
    }
)
public class DocumentTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    protected DocumentTag() {
        // JPA 전용
    }

    private DocumentTag(Document document, Tag tag) {
        this.document = document;
        this.tag      = tag;
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    public static DocumentTag create(Document document, Tag tag) {
        return new DocumentTag(document, tag);
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

    public Tag getTag() {
        return tag;
    }

    public Long getTagId() {
        return tag.getId();
    }
}
