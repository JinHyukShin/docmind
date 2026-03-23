package com.docmind.search.controller;

import com.docmind.global.common.ApiResponse;
import com.docmind.global.exception.BusinessException;
import com.docmind.global.exception.ErrorCode;
import com.docmind.global.security.CustomUserDetails;
import com.docmind.search.dto.SearchResponse;
import com.docmind.search.dto.SearchResult;
import com.docmind.search.service.FullTextSearchService;
import com.docmind.search.service.HybridSearchService;
import com.docmind.search.service.SemanticSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 통합 검색 API 컨트롤러.
 *
 * GET /api/v1/search?q={query}&type={full_text|semantic|hybrid}&page=0&size=20
 *
 * type 파라미터:
 *   - full_text : PostgreSQL tsvector 전문검색
 *   - semantic  : pgvector 코사인 유사도 시맨틱 검색
 *   - hybrid    : RRF 하이브리드 검색 (기본값)
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private static final int DEFAULT_SIZE = 20;

    private final FullTextSearchService fullTextSearchService;
    private final SemanticSearchService semanticSearchService;
    private final HybridSearchService hybridSearchService;

    public SearchController(FullTextSearchService fullTextSearchService,
                            SemanticSearchService semanticSearchService,
                            HybridSearchService hybridSearchService) {
        this.fullTextSearchService = fullTextSearchService;
        this.semanticSearchService = semanticSearchService;
        this.hybridSearchService = hybridSearchService;
    }

    /**
     * 검색 API.
     *
     * @param q       검색 쿼리 (필수)
     * @param type    검색 유형 (full_text | semantic | hybrid, 기본값: hybrid)
     * @param page    페이지 번호 (0-based, 기본값: 0)
     * @param size    페이지 크기 (기본값: 20)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "hybrid") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (q == null || q.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Query parameter 'q' is required");
        }

        Long userId = userDetails.getUserId();
        int topK = size; // 현재 단순 구현: topK = size (페이징 미적용)

        List<SearchResult> results = switch (type.toLowerCase()) {
            case "full_text" -> fullTextSearchService.search(q, userId, topK);
            case "semantic"  -> semanticSearchService.search(q, userId, topK);
            case "hybrid"    -> hybridSearchService.hybridSearch(q, userId, topK);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "Invalid search type: '" + type + "'. Allowed: full_text, semantic, hybrid");
        };

        SearchResponse response = SearchResponse.of(results, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
