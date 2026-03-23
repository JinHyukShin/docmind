package com.docmind.ai.repository;

import com.docmind.ai.entity.DocumentChunk;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * DocumentChunk 리포지토리.
 *
 * 핵심 쿼리:
 *   1) pgvector 코사인 유사도 ANN 검색 (HNSW idx_chunk_embedding 활용)
 *   2) tsvector 전문검색 (GIN idx_chunk_content_tsv 활용)
 *   3) document_id별 청크 목록 (idx_chunk_document_id 활용)
 *
 * 네이티브 쿼리 사용 이유:
 *   - pgvector의 <=> 연산자, to_tsquery() 등은 JPQL이 지원하지 않음.
 *   - CAST(:queryVector AS vector): Spring Data가 String 파라미터를
 *     vector 타입으로 변환하기 위해 명시적 캐스팅 필요.
 *     파라미터는 "[0.1, 0.2, ...]" 형식의 문자열로 전달.
 */
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /**
     * pgvector 코사인 유사도 ANN 검색 (시맨틱 검색).
     *
     * - idx_chunk_embedding HNSW 인덱스 활용 → 수백만 건에서도 밀리초 응답.
     * - <=> : pgvector 코사인 거리 연산자 (0 = 동일, 2 = 반대).
     * - score = 1 - distance: 유사도 (1에 가까울수록 유사).
     * - CAST(:queryVector AS vector): float[] → vector 타입 변환.
     *   파라미터 형식 예시: "[0.123, -0.456, ...]" (1024차원)
     * - d.user_id 필터: 사용자 격리 (타 사용자 문서 검색 차단).
     * - d.id IN :documentIds: 특정 문서 내에서만 검색.
     * - d.status = 'READY': 임베딩 완료된 문서만 검색 대상.
     *
     * @param queryVector 쿼리 임베딩 벡터 (문자열 형식: "[v1, v2, ...]")
     * @param userId      검색 요청 사용자 ID (격리)
     * @param documentIds 검색 대상 문서 ID 목록
     * @param topK        반환할 최대 청크 수
     * @return 유사도 내림차순 청크 목록 (score 컬럼 포함)
     */
    @Query(value = """
        SELECT dc.*, 1 - (dc.embedding <=> CAST(:queryVector AS vector)) AS score
        FROM document_chunk dc
        JOIN document d ON dc.document_id = d.id
        WHERE d.user_id = :userId
          AND d.id IN :documentIds
          AND d.status = 'READY'
        ORDER BY dc.embedding <=> CAST(:queryVector AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<DocumentChunk> findTopKBySimilarity(
        @Param("queryVector") String queryVector,
        @Param("userId") Long userId,
        @Param("documentIds") List<Long> documentIds,
        @Param("topK") int topK
    );

    /**
     * tsvector 전문검색 (Full-Text Search).
     *
     * - idx_chunk_content_tsv GIN 인덱스 활용 → to_tsquery() 조회 가속.
     * - plainto_tsquery('simple', :query): 공백 기준 AND 조건 자동 생성.
     *   'simple' 딕셔너리: 어간 분석 없이 정확 매칭 (한국어 호환).
     * - ts_rank(): 관련도 점수 계산 (GIN 인덱스와 함께 사용).
     * - @@ : tsvector와 tsquery 일치 연산자.
     *
     * @param query       전문검색 쿼리 문자열
     * @param userId      검색 요청 사용자 ID (격리)
     * @param documentIds 검색 대상 문서 ID 목록
     * @param limit       반환할 최대 청크 수
     * @return 관련도 내림차순 청크 목록
     */
    @Query(value = """
        SELECT dc.*, ts_rank(dc.content_tsv, plainto_tsquery('simple', :query)) AS score
        FROM document_chunk dc
        JOIN document d ON dc.document_id = d.id
        WHERE d.user_id = :userId
          AND d.id IN :documentIds
          AND d.status = 'READY'
          AND dc.content_tsv @@ plainto_tsquery('simple', :query)
        ORDER BY score DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<DocumentChunk> findByFullTextSearch(
        @Param("query") String query,
        @Param("userId") Long userId,
        @Param("documentIds") List<Long> documentIds,
        @Param("limit") int limit
    );

    /**
     * tsvector 전문검색 - 점수 및 문서 제목 포함 결과 반환.
     *
     * 검색 서비스에서 score와 documentTitle을 직접 사용하기 위해
     * Object[] 배열로 반환한다.
     *
     * 반환 컬럼 순서:
     *   [0] chunk_id      (Long)
     *   [1] document_id   (Long)
     *   [2] document_title (String)
     *   [3] content       (String)
     *   [4] score         (Double)  -- ts_rank
     */
    @Query(value = """
        SELECT dc.id         AS chunk_id,
               d.id          AS document_id,
               d.title       AS document_title,
               dc.content    AS content,
               ts_rank(dc.content_tsv, plainto_tsquery('simple', :query)) AS score
        FROM document_chunk dc
        JOIN document d ON dc.document_id = d.id
        WHERE d.user_id = :userId
          AND d.id IN :documentIds
          AND d.status = 'READY'
          AND dc.content_tsv @@ plainto_tsquery('simple', :query)
        ORDER BY score DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findByFullTextSearchWithScore(
        @Param("query") String query,
        @Param("userId") Long userId,
        @Param("documentIds") List<Long> documentIds,
        @Param("limit") int limit
    );

    /**
     * pgvector 코사인 유사도 검색 - 점수 및 문서 제목 포함 결과 반환.
     *
     * 반환 컬럼 순서:
     *   [0] chunk_id       (Long)
     *   [1] document_id    (Long)
     *   [2] document_title (String)
     *   [3] content        (String)
     *   [4] score          (Double)  -- 1 - cosine_distance
     */
    @Query(value = """
        SELECT dc.id         AS chunk_id,
               d.id          AS document_id,
               d.title       AS document_title,
               dc.content    AS content,
               1 - (dc.embedding <=> CAST(:queryVector AS vector)) AS score
        FROM document_chunk dc
        JOIN document d ON dc.document_id = d.id
        WHERE d.user_id = :userId
          AND d.id IN :documentIds
          AND d.status = 'READY'
        ORDER BY dc.embedding <=> CAST(:queryVector AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<Object[]> findTopKBySimilarityWithScore(
        @Param("queryVector") String queryVector,
        @Param("userId") Long userId,
        @Param("documentIds") List<Long> documentIds,
        @Param("topK") int topK
    );

    /**
     * 문서별 청크 목록 조회 (chunk_index 순서 보장).
     * idx_chunk_document_id + chunk_index 인덱스 활용.
     */
    List<DocumentChunk> findAllByDocumentIdOrderByChunkIndex(Long documentId);

    /**
     * 문서별 임베딩 미할당 청크 목록 조회 (EMBEDDING 단계 재처리용).
     * idx_chunk_document_id + embedding IS NULL 필터.
     */
    @Query("SELECT dc FROM DocumentChunk dc WHERE dc.document.id = :documentId AND dc.embedding IS NULL ORDER BY dc.chunkIndex")
    List<DocumentChunk> findUnembeddedByDocumentId(@Param("documentId") Long documentId);

    /**
     * 문서 청크 전체 삭제 (문서 재처리 시 초기화).
     * 벌크 DELETE: idx_chunk_document_id Range Scan.
     */
    @Modifying
    @Query("DELETE FROM DocumentChunk dc WHERE dc.document.id = :documentId")
    void deleteAllByDocumentId(@Param("documentId") Long documentId);

    /**
     * 문서 청크 수 집계 (Document.chunkCount 갱신용).
     */
    long countByDocumentId(Long documentId);

    /**
     * ID 목록으로 청크 조회 (RAG 컨텍스트 조립용).
     * @EntityGraph로 Document를 LEFT JOIN FETCH하여 N+1 방지.
     */
    @EntityGraph(attributePaths = {"document"})
    List<DocumentChunk> findAllById(Iterable<Long> ids);
}
