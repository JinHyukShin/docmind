package com.docmind.tag.repository;

import com.docmind.tag.entity.DocumentTag;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * DocumentTag 리포지토리.
 *
 * 인덱스 활용:
 *   - findAllByDocumentId : idx_document_tag_document → 문서 태그 목록 조회
 *   - findAllByTagId      : idx_document_tag_tag      → 태그별 문서 목록 조회
 *   - deleteByDocumentIdAndTagId : (document_id, tag_id) UNIQUE 인덱스 → 단건 삭제
 *
 * N+1 방지:
 *   - @EntityGraph로 tag 또는 document를 LEFT JOIN FETCH로 즉시 로딩.
 */
public interface DocumentTagRepository extends JpaRepository<DocumentTag, Long> {

    /**
     * 문서의 태그 목록 조회 (Tag FETCH JOIN으로 N+1 방지).
     * idx_document_tag_document + EntityGraph(tag) 활용.
     */
    @EntityGraph(attributePaths = {"tag"})
    List<DocumentTag> findAllByDocumentId(Long documentId);

    /**
     * 태그별 문서 목록 조회 (Document FETCH JOIN으로 N+1 방지).
     * idx_document_tag_tag + EntityGraph(document) 활용.
     */
    @EntityGraph(attributePaths = {"document"})
    List<DocumentTag> findAllByTagId(Long tagId);

    /**
     * 특정 문서-태그 연결 존재 여부 확인 (중복 추가 방지).
     * (document_id, tag_id) UNIQUE 인덱스 → Index Scan.
     */
    boolean existsByDocumentIdAndTagId(Long documentId, Long tagId);

    /**
     * 특정 문서-태그 연결 삭제.
     * 벌크 DELETE: (document_id, tag_id) UNIQUE 인덱스 활용.
     */
    @Modifying
    @Query("DELETE FROM DocumentTag dt WHERE dt.document.id = :documentId AND dt.tag.id = :tagId")
    void deleteByDocumentIdAndTagId(
        @Param("documentId") Long documentId,
        @Param("tagId") Long tagId
    );

    /**
     * 태그별 문서 연결 목록 페이징 조회 (Document FETCH JOIN).
     * idx_document_tag_tag + EntityGraph(document) 활용.
     */
    @EntityGraph(attributePaths = {"document"})
    Page<DocumentTag> findAllByTagId(Long tagId, Pageable pageable);

    /**
     * 특정 문서의 모든 태그 연결 삭제 (문서 삭제 시 ON DELETE CASCADE로 처리되나,
     * 명시적 벌크 DELETE가 필요한 경우를 위해 제공).
     */
    @Modifying
    @Query("DELETE FROM DocumentTag dt WHERE dt.document.id = :documentId")
    void deleteAllByDocumentId(@Param("documentId") Long documentId);
}
