package com.docmind.document.repository;

import com.docmind.document.entity.Document;
import com.docmind.document.entity.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Document 리포지토리.
 *
 * 인덱스 활용:
 *   - findAllByUserId     : idx_document_user_id → 사용자 문서 목록 조회
 *   - findAllByStatus     : idx_document_status  → 파이프라인 워커 처리 대기 조회
 *   - findByIdAndUserId   : PK(id) + idx_document_user_id → 소유권 검증 단건 조회
 *
 * N+1 방지:
 *   - @EntityGraph로 User를 LEFT JOIN FETCH하여 한 번의 쿼리로 로딩.
 *     단, 목록 페이징 시에는 CountQuery 분리가 필요하므로 @Query 병행.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * 사용자 문서 목록 페이징 조회.
     * idx_document_user_id + created_at DESC 인덱스 활용.
     * Pageable의 sort에 "createdAt" 사용 권장.
     */
    Page<Document> findAllByUserId(Long userId, Pageable pageable);

    /**
     * 사용자 문서 단건 조회 (소유권 검증 포함).
     * PK 조회 + user_id 필터로 타 사용자 문서 접근 차단.
     */
    Optional<Document> findByIdAndUserId(Long id, Long userId);

    /**
     * 특정 상태의 문서 목록 조회 (파이프라인 워커용).
     * idx_document_status → Index Scan.
     */
    List<Document> findAllByStatus(DocumentStatus status);

    /**
     * 사용자의 특정 상태 문서 목록 조회.
     * idx_document_user_id + status 복합 필터.
     */
    @Query("SELECT d FROM Document d WHERE d.user.id = :userId AND d.status = :status")
    List<Document> findAllByUserIdAndStatus(
        @Param("userId") Long userId,
        @Param("status") DocumentStatus status
    );

    /**
     * 문서 상세 조회 (User FETCH JOIN으로 N+1 방지).
     * EntityGraph: user 연관을 LEFT JOIN FETCH로 즉시 로딩.
     */
    @EntityGraph(attributePaths = {"user"})
    Optional<Document> findWithUserById(Long id);
}
