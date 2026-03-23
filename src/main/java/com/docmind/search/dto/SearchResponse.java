package com.docmind.search.dto;

import java.util.List;

/**
 * 검색 응답 DTO.
 *
 * results: 검색 결과 목록
 * totalCount: 전체 결과 수 (페이징 기준)
 * page: 현재 페이지 (0-based)
 * size: 페이지 크기
 */
public record SearchResponse(
        List<SearchResult> results,
        long totalCount,
        int page,
        int size
) {

    public static SearchResponse of(List<SearchResult> results, int page, int size) {
        return new SearchResponse(results, results.size(), page, size);
    }
}
