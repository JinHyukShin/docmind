package com.docmind.search.service;

import com.docmind.ai.repository.DocumentChunkRepository;
import com.docmind.ai.service.EmbeddingService;
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
 * pgvector 코사인 유사도 기반 시맨틱 검색 서비스.
 *
 * 1. 질문 텍스트를 Voyage AI로 임베딩 (input_type="query")
 * 2. pgvector HNSW 인덱스를 활용한 코사인 유사도 Top-K 검색
 * 3. score = 1 - cosine_distance (1에 가까울수록 유사)
 *
 * 검색 대상: 사용자의 모든 READY 문서 청크.
 *
 * Object[] 매핑 순서 (findTopKBySimilarityWithScore):
 *   [0] chunk_id       (Number -> Long)
 *   [1] document_id    (Number -> Long)
 *   [2] document_title (String)
 *   [3] content        (String)
 *   [4] score          (Number -> double)
 */
@Service
@Transactional(readOnly = true)
public class SemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);

    private final DocumentChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;

    public SemanticSearchService(DocumentChunkRepository chunkRepository,
                                 DocumentRepository documentRepository,
                                 EmbeddingService embeddingService) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.embeddingService = embeddingService;
    }

    /**
     * 시맨틱 검색을 수행한다.
     *
     * @param query  검색 쿼리 문자열
     * @param userId 검색 요청 사용자 ID (격리)
     * @param topK   반환할 최대 결과 수
     * @return 코사인 유사도 내림차순 SearchResult 목록
     */
    public List<SearchResult> search(String query, Long userId, int topK) {
        log.debug("Semantic search: query='{}', userId={}, topK={}", query, userId, topK);

        // 사용자의 READY 문서 ID 목록 조회
        List<Long> documentIds = documentRepository
                .findAllByUserIdAndStatus(userId, DocumentStatus.READY)
                .stream()
                .map(Document::getId)
                .toList();

        if (documentIds.isEmpty()) {
            return List.of();
        }

        // 질문 임베딩 생성 (input_type="query")
        float[] queryEmbedding = embeddingService.embedQuery(query);
        String queryVector = embeddingToString(queryEmbedding);

        List<Object[]> rows = chunkRepository.findTopKBySimilarityWithScore(
                queryVector, userId, documentIds, topK);

        return rows.stream()
                .map(row -> new SearchResult(
                        toLong(row[0]),
                        toLong(row[1]),
                        (String) row[2],
                        (String) row[3],
                        toDouble(row[4]),
                        "SEMANTIC"
                ))
                .toList();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * float[] 벡터를 pgvector 파라미터 문자열로 변환한다.
     * 예: "[0.123, -0.456, ...]"
     */
    private static String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static Long toLong(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        return null;
    }

    private static double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
