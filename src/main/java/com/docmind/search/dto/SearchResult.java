package com.docmind.search.dto;

/**
 * 검색 결과 단건 DTO.
 *
 * withScore(): RRF 병합 시 점수를 교체한 새 레코드를 반환한다.
 * chunkId: 전문검색 / 시맨틱 검색 결과를 RRF로 병합할 때 키로 사용.
 */
public record SearchResult(
        Long chunkId,
        Long documentId,
        String documentTitle,
        String content,
        double score,
        String searchType
) {

    /** RRF 병합 후 점수를 교체한 새 SearchResult를 반환한다. */
    public SearchResult withScore(double newScore) {
        return new SearchResult(chunkId, documentId, documentTitle, content, newScore, searchType);
    }
}
