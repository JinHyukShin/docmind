package com.docmind.search.service;

import com.docmind.search.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF(Reciprocal Rank Fusion) 기반 하이브리드 검색 서비스.
 *
 * 전문검색(tsvector)과 시맨틱 검색(pgvector) 결과를 RRF 알고리즘으로 병합한다.
 *
 * RRF 알고리즘 (설계서 7.6절):
 *   - 공식: RRF_score = SUM(1 / (k + rank_i))
 *   - k = 60 (낮은 순위 결과의 영향력 감쇠 상수)
 *   - rank는 0-based → rank_i + 1로 1-based 처리
 *   - 두 검색 결과 집합에서 동일 chunkId가 나타날 경우 점수를 합산
 *
 * 처리 흐름:
 *   1. 전문검색  topK*2 결과 조회
 *   2. 시맨틱 검색 topK*2 결과 조회
 *   3. 두 결과를 chunkId 기준으로 RRF 점수 계산 및 합산
 *   4. RRF 점수 내림차순 정렬 후 topK 반환
 */
@Service
public class HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);

    /**
     * RRF 상수 k=60.
     * 값이 클수록 낮은 순위 결과의 영향력이 줄어든다.
     * 논문 권장값은 60.
     */
    private static final int RRF_K = 60;

    private final FullTextSearchService fullTextSearch;
    private final SemanticSearchService semanticSearch;

    public HybridSearchService(FullTextSearchService fullTextSearch,
                               SemanticSearchService semanticSearch) {
        this.fullTextSearch = fullTextSearch;
        this.semanticSearch = semanticSearch;
    }

    /**
     * 하이브리드 검색을 수행한다.
     *
     * @param query  검색 쿼리 문자열
     * @param userId 검색 요청 사용자 ID (격리)
     * @param topK   반환할 최대 결과 수
     * @return RRF 점수 내림차순 SearchResult 목록
     */
    public List<SearchResult> hybridSearch(String query, Long userId, int topK) {
        log.debug("Hybrid search: query='{}', userId={}, topK={}", query, userId, topK);

        // 1. 전문검색 (tsvector) - topK*2로 넓게 수집
        List<SearchResult> ftResults = fullTextSearch.search(query, userId, topK * 2);

        // 2. 시맨틱 검색 (pgvector) - topK*2로 넓게 수집
        List<SearchResult> semResults = semanticSearch.search(query, userId, topK * 2);

        // 3. RRF 점수 계산
        //    각 결과 목록에서 rank(0-based)를 기반으로 1/(k + rank + 1) 점수를 부여하고 합산
        Map<Long, Double> rrfScores = new HashMap<>();

        for (int rank = 0; rank < ftResults.size(); rank++) {
            Long chunkId = ftResults.get(rank).chunkId();
            rrfScores.merge(chunkId, 1.0 / (RRF_K + rank + 1), Double::sum);
        }

        for (int rank = 0; rank < semResults.size(); rank++) {
            Long chunkId = semResults.get(rank).chunkId();
            rrfScores.merge(chunkId, 1.0 / (RRF_K + rank + 1), Double::sum);
        }

        // 4. 원본 SearchResult 조회용 맵 구성
        //    전문검색 결과를 먼저 넣고, 시맨틱 결과는 중복 시 덮어쓰지 않음(putIfAbsent)
        Map<Long, SearchResult> allResults = new HashMap<>();
        ftResults.forEach(r -> allResults.put(r.chunkId(), r));
        semResults.forEach(r -> allResults.putIfAbsent(r.chunkId(), r));

        // 5. RRF 점수 내림차순 정렬 후 topK 반환
        //    withScore()로 RRF 점수를 교체하고 searchType을 "HYBRID"로 재설정
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    SearchResult original = allResults.get(entry.getKey());
                    return new SearchResult(
                            original.chunkId(),
                            original.documentId(),
                            original.documentTitle(),
                            original.content(),
                            entry.getValue(),
                            "HYBRID"
                    );
                })
                .toList();
    }
}
