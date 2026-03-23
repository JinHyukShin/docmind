package com.docmind.search.service;

import com.docmind.ai.repository.DocumentChunkRepository;
import com.docmind.document.entity.Document;
import com.docmind.document.entity.DocumentStatus;
import com.docmind.document.repository.DocumentRepository;
import com.docmind.search.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PostgreSQL tsvector 기반 전문검색 서비스.
 *
 * plainto_tsquery('simple', query) + ts_rank 를 사용하여
 * 사용자 소유의 READY 상태 청크를 관련도 순으로 반환한다.
 *
 * 검색 대상: 사용자의 모든 READY 문서 청크.
 * - 문서 ID 목록을 먼저 조회한 뒤 DocumentChunkRepository.findByFullTextSearchWithScore() 활용.
 *
 * Object[] 매핑 순서 (findByFullTextSearchWithScore):
 *   [0] chunk_id      (Number -> Long)
 *   [1] document_id   (Number -> Long)
 *   [2] document_title (String)
 *   [3] content       (String)
 *   [4] score         (Number -> double)
 */
@Service
@Transactional(readOnly = true)
public class FullTextSearchService {

    private static final Logger log = LoggerFactory.getLogger(FullTextSearchService.class);

    private final DocumentChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;

    public FullTextSearchService(DocumentChunkRepository chunkRepository,
                                 DocumentRepository documentRepository) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
    }

    /**
     * tsvector 전문검색을 수행한다.
     *
     * @param query  검색 쿼리 문자열
     * @param userId 검색 요청 사용자 ID (격리)
     * @param topK   반환할 최대 결과 수
     * @return 관련도(ts_rank) 내림차순 SearchResult 목록
     */
    public List<SearchResult> search(String query, Long userId, int topK) {
        log.debug("FullText search: query='{}', userId={}, topK={}", query, userId, topK);

        // 사용자의 READY 문서 ID 목록 조회
        List<Long> documentIds = documentRepository
                .findAllByUserIdAndStatus(userId, DocumentStatus.READY)
                .stream()
                .map(Document::getId)
                .toList();

        if (documentIds.isEmpty()) {
            return List.of();
        }

        List<Object[]> rows = chunkRepository.findByFullTextSearchWithScore(
                query, userId, documentIds, topK);

        return rows.stream()
                .map(row -> new SearchResult(
                        toLong(row[0]),
                        toLong(row[1]),
                        (String) row[2],
                        (String) row[3],
                        toDouble(row[4]),
                        "FULL_TEXT"
                ))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Helper: JDBC 타입 변환
    // -------------------------------------------------------------------------

    private static Long toLong(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        return null;
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
