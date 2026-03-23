package com.docmind.search.dto;

/**
 * 검색 요청 파라미터 DTO.
 *
 * type: "full_text" | "semantic" | "hybrid" (기본값: hybrid)
 */
public record SearchRequest(
        String q,
        String type,
        int page,
        int size
) {

    public SearchRequest {
        if (type == null || type.isBlank()) {
            type = "hybrid";
        }
        if (size <= 0) {
            size = 20;
        }
        if (page < 0) {
            page = 0;
        }
    }
}
