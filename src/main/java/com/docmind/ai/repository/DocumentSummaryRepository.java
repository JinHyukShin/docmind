package com.docmind.ai.repository;

import com.docmind.ai.entity.DocumentSummary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * DocumentSummary 리포지토리.
 *
 * document_id UNIQUE 제약 인덱스 활용:
 *   - findByDocumentId : 문서별 요약 단건 조회 (가장 빈번)
 *
 * N+1 방지:
 *   - findWithDocumentByDocumentId: Document를 LEFT JOIN FETCH로 즉시 로딩.
 */
public interface DocumentSummaryRepository extends JpaRepository<DocumentSummary, Long> {

    /**
     * 문서 ID로 요약 단건 조회.
     * document_id UNIQUE 인덱스 → Index Scan.
     */
    Optional<DocumentSummary> findByDocumentId(Long documentId);

    /**
     * 문서 요약 존재 여부 확인.
     * EXISTS 쿼리로 변환 → COUNT(*) 보다 효율적.
     */
    boolean existsByDocumentId(Long documentId);

    /**
     * 문서 요약 조회 (Document FETCH JOIN으로 N+1 방지).
     * EntityGraph: document 연관을 LEFT JOIN FETCH로 즉시 로딩.
     */
    @EntityGraph(attributePaths = {"document"})
    Optional<DocumentSummary> findWithDocumentByDocumentId(Long documentId);

    /**
     * 문서 요약 삭제 (재생성 시 기존 요약 제거).
     */
    void deleteByDocumentId(Long documentId);
}
