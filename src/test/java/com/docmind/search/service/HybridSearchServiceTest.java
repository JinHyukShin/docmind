package com.docmind.search.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * HybridSearchService 단위 테스트.
 *
 * 설계서 기준:
 *   - RRF(Reciprocal Rank Fusion) = Σ 1 / (k + rank_i), k=60 권장
 *   - 전문검색(Full-Text) + 시맨틱 검색(Semantic) 결과를 RRF로 병합
 *
 * HybridSearchService는 아직 구현되지 않았으므로(src/main/java/.../search/service/ 비어 있음),
 * 이 테스트는 RRF 로직의 계약(contract)을 명세한다.
 * 내부적으로 RRF 계산 로직만 검증 가능한 순수 단위 테스트를 포함한다.
 */
@DisplayName("HybridSearchService 단위 테스트 (RRF 계산)")
class HybridSearchServiceTest {

    // -------------------------------------------------------------------------
    // RRF 헬퍼: 서비스 구현 전 테스트용 인라인 구현
    // HybridSearchService 구현 후에는 서비스 호출로 대체한다.
    // -------------------------------------------------------------------------

    private static final int RRF_K = 60;

    /**
     * RRF 점수 계산: score = 1 / (k + rank)
     * rank는 1-based (1이 1위)
     */
    private double rrfScore(int rank) {
        return 1.0 / (RRF_K + rank);
    }

    /**
     * 두 랭킹 리스트를 RRF로 병합한다.
     *
     * @param listA 첫 번째 검색 결과 (문서 ID 순서, 1위부터)
     * @param listB 두 번째 검색 결과 (문서 ID 순서, 1위부터)
     * @return 문서 ID → RRF 합산 점수 맵
     */
    private Map<String, Double> computeRrf(List<String> listA, List<String> listB) {
        java.util.HashMap<String, Double> scores = new java.util.HashMap<>();

        for (int i = 0; i < listA.size(); i++) {
            String id = listA.get(i);
            scores.merge(id, rrfScore(i + 1), Double::sum);
        }
        for (int i = 0; i < listB.size(); i++) {
            String id = listB.get(i);
            scores.merge(id, rrfScore(i + 1), Double::sum);
        }

        return Collections.unmodifiableMap(scores);
    }

    /**
     * RRF 점수 맵을 내림차순으로 정렬하여 ID 리스트를 반환한다.
     */
    private List<String> rankByRrf(Map<String, Double> scores) {
        List<String> ranked = new ArrayList<>(scores.keySet());
        ranked.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        return ranked;
    }

    // =========================================================================
    // 1. RRF 점수 계산 정확성
    // =========================================================================

    @Test
    @DisplayName("RRF 점수 = 1/(k+rank), k=60, 1위 점수는 1/61 이다")
    void rrfScore_rank1_equals1over61() {
        // when
        double score = rrfScore(1);

        // then
        double expected = 1.0 / 61.0;
        assertThat(score).isCloseTo(expected, within(1e-10));
    }

    @Test
    @DisplayName("RRF 점수: 순위가 높을수록(1위에 가까울수록) 점수가 크다")
    void rrfScore_higherRankHasHigherScore() {
        // when
        double scoreRank1 = rrfScore(1);
        double scoreRank2 = rrfScore(2);
        double scoreRank10 = rrfScore(10);

        // then
        assertThat(scoreRank1).isGreaterThan(scoreRank2);
        assertThat(scoreRank2).isGreaterThan(scoreRank10);
    }

    @Test
    @DisplayName("두 검색 결과 모두 1위인 문서는 가장 높은 RRF 합산 점수를 가진다")
    void rrf_documentRank1InBothLists_hasHighestCombinedScore() {
        // given
        // doc-A: 전문검색 1위, 시맨틱 1위 → RRF = 1/61 + 1/61 ≈ 0.03279
        // doc-B: 전문검색 2위, 시맨틱 2위 → RRF = 1/62 + 1/62 ≈ 0.03226
        List<String> fullTextRanking = List.of("doc-A", "doc-B", "doc-C");
        List<String> semanticRanking = List.of("doc-A", "doc-B", "doc-D");

        // when
        Map<String, Double> scores = computeRrf(fullTextRanking, semanticRanking);

        // then
        assertThat(scores.get("doc-A")).isGreaterThan(scores.get("doc-B"));
    }

    @Test
    @DisplayName("RRF 합산: doc-A가 두 리스트에 모두 있으면 한 리스트에만 있는 문서보다 점수가 높다")
    void rrf_documentInBothLists_scoresHigherThanDocumentInOneList() {
        // given
        // doc-A: 전문검색 1위, 시맨틱 1위 → 2배 반영
        // doc-E: 전문검색 2위, 시맨틱에 없음 → 단일 반영
        List<String> fullTextRanking = List.of("doc-A", "doc-E");
        List<String> semanticRanking = List.of("doc-A");

        // when
        Map<String, Double> scores = computeRrf(fullTextRanking, semanticRanking);

        // then
        double scoreA = scores.get("doc-A"); // 1/61 + 1/61
        double scoreE = scores.get("doc-E"); // 1/62 (시맨틱 없음)
        assertThat(scoreA).isGreaterThan(scoreE);
    }

    // =========================================================================
    // 2. 전문검색에만 매칭되는 결과 포함 검증
    // =========================================================================

    @Test
    @DisplayName("전문검색에만 매칭되는 문서가 최종 결과에 포함된다")
    void hybridSearch_fullTextOnlyMatch_includedInResult() {
        // given
        // doc-FT: 전문검색 1위, 시맨틱에 없음
        // doc-SEM: 시맨틱 1위, 전문검색에 없음
        List<String> fullTextRanking = List.of("doc-FT", "doc-BOTH");
        List<String> semanticRanking = List.of("doc-SEM", "doc-BOTH");

        // when
        Map<String, Double> scores = computeRrf(fullTextRanking, semanticRanking);
        List<String> ranked = rankByRrf(scores);

        // then: doc-FT가 결과에 포함되어야 한다
        assertThat(ranked).contains("doc-FT");
    }

    @Test
    @DisplayName("전문검색에만 매칭되는 문서의 RRF 점수가 양수이다")
    void hybridSearch_fullTextOnlyMatch_hasPositiveScore() {
        // given
        List<String> fullTextRanking = List.of("doc-FT-ONLY");
        List<String> semanticRanking = List.of("doc-SEM-ONLY");

        // when
        Map<String, Double> scores = computeRrf(fullTextRanking, semanticRanking);

        // then
        assertThat(scores.get("doc-FT-ONLY")).isPositive();
    }

    // =========================================================================
    // 3. 시맨틱 검색에만 매칭되는 결과 포함 검증
    // =========================================================================

    @Test
    @DisplayName("시맨틱 검색에만 매칭되는 문서가 최종 결과에 포함된다")
    void hybridSearch_semanticOnlyMatch_includedInResult() {
        // given
        List<String> fullTextRanking = List.of("doc-FT", "doc-BOTH");
        List<String> semanticRanking = List.of("doc-SEM", "doc-BOTH");

        // when
        Map<String, Double> scores = computeRrf(fullTextRanking, semanticRanking);
        List<String> ranked = rankByRrf(scores);

        // then: doc-SEM이 결과에 포함되어야 한다
        assertThat(ranked).contains("doc-SEM");
    }

    @Test
    @DisplayName("시맨틱 검색에만 매칭되는 문서의 RRF 점수가 양수이다")
    void hybridSearch_semanticOnlyMatch_hasPositiveScore() {
        // given
        List<String> fullTextRanking = List.of("doc-FT-ONLY");
        List<String> semanticRanking = List.of("doc-SEM-ONLY");

        // when
        Map<String, Double> scores = computeRrf(fullTextRanking, semanticRanking);

        // then
        assertThat(scores.get("doc-SEM-ONLY")).isPositive();
    }

    // =========================================================================
    // 4. 엣지 케이스
    // =========================================================================

    @Test
    @DisplayName("두 결과 모두 빈 리스트이면 최종 결과도 비어 있다")
    void hybridSearch_bothEmpty_returnsEmpty() {
        // given
        List<String> fullTextRanking = List.of();
        List<String> semanticRanking = List.of();

        // when
        Map<String, Double> scores = computeRrf(fullTextRanking, semanticRanking);

        // then
        assertThat(scores).isEmpty();
    }

    @Test
    @DisplayName("전문검색 결과만 있을 때 해당 결과가 그대로 반환된다")
    void hybridSearch_onlyFullTextResults_returnsThem() {
        // given
        List<String> fullTextRanking = List.of("doc-1", "doc-2", "doc-3");
        List<String> semanticRanking = List.of();

        // when
        Map<String, Double> scores = computeRrf(fullTextRanking, semanticRanking);
        List<String> ranked = rankByRrf(scores);

        // then
        assertThat(ranked).containsExactlyInAnyOrder("doc-1", "doc-2", "doc-3");
        // 순위 순서도 보존되어야 한다 (전문검색 1위가 최고 RRF 점수)
        assertThat(ranked.get(0)).isEqualTo("doc-1");
    }

    @Test
    @DisplayName("시맨틱 검색 결과만 있을 때 해당 결과가 그대로 반환된다")
    void hybridSearch_onlySemanticResults_returnsThem() {
        // given
        List<String> fullTextRanking = List.of();
        List<String> semanticRanking = List.of("doc-X", "doc-Y");

        // when
        Map<String, Double> scores = computeRrf(fullTextRanking, semanticRanking);
        List<String> ranked = rankByRrf(scores);

        // then
        assertThat(ranked).containsExactlyInAnyOrder("doc-X", "doc-Y");
        assertThat(ranked.get(0)).isEqualTo("doc-X");
    }

    @Test
    @DisplayName("두 리스트에 모두 있는 문서가 한 리스트에만 있는 문서보다 높은 순위를 가진다 (동일 rank 기준)")
    void hybridSearch_documentInBoth_ranksHigherThanDocumentInOne() {
        // given
        // doc-BOTH: 두 리스트 모두 2위 → RRF = 1/62 + 1/62 ≈ 0.03226
        // doc-FT:   전문검색 1위, 시맨틱 없음 → RRF = 1/61 ≈ 0.01639
        // → doc-BOTH 가 doc-FT 보다 높아야 한다
        List<String> fullTextRanking = List.of("doc-FT", "doc-BOTH");
        List<String> semanticRanking = List.of("doc-SEM", "doc-BOTH");

        // when
        Map<String, Double> scores = computeRrf(fullTextRanking, semanticRanking);

        // then
        assertThat(scores.get("doc-BOTH")).isGreaterThan(scores.get("doc-FT"));
    }

    // =========================================================================
    // 5. RRF 점수 합산 정확성 (수치 검증)
    // =========================================================================

    @Test
    @DisplayName("두 리스트 모두 1위 문서의 RRF 합산 점수는 2/(k+1) 이다")
    void rrfScore_rank1InBothLists_equals2Over61() {
        // given
        List<String> listA = List.of("doc-A");
        List<String> listB = List.of("doc-A");

        // when
        Map<String, Double> scores = computeRrf(listA, listB);

        // then
        double expected = 2.0 / (RRF_K + 1);
        assertThat(scores.get("doc-A")).isCloseTo(expected, within(1e-10));
    }

    @Test
    @DisplayName("리스트 A 1위 + 리스트 B 2위 문서의 RRF 합산은 1/61 + 1/62 이다")
    void rrfScore_rank1InA_rank2InB_correctSum() {
        // given
        List<String> listA = List.of("doc-TARGET", "doc-OTHER");
        List<String> listB = List.of("doc-FIRST", "doc-TARGET");

        // when
        Map<String, Double> scores = computeRrf(listA, listB);

        // then
        double expected = (1.0 / 61) + (1.0 / 62);
        assertThat(scores.get("doc-TARGET")).isCloseTo(expected, within(1e-10));
    }
}
