package com.docmind.ai.controller;

import com.docmind.ai.dto.SummaryResponse;
import com.docmind.ai.service.SummaryService;
import com.docmind.global.common.ApiResponse;
import com.docmind.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 요약 컨트롤러.
 *
 * POST /api/v1/documents/{documentId}/summary -- 요약 생성 (SSE 스트리밍)
 * GET  /api/v1/documents/{documentId}/summary -- 저장된 요약 조회
 * DELETE /api/v1/documents/{documentId}/summary -- 요약 삭제 (재생성용)
 */
@Tag(name = "AI Summary", description = "문서 AI 요약 API")
@RestController
@RequestMapping("/api/v1/documents/{documentId}/summary")
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @Operation(summary = "요약 생성 (SSE 스트리밍)")
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateSummary(
            @PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return summaryService.generateSummary(documentId, user.getUserId());
    }

    @Operation(summary = "저장된 요약 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<SummaryResponse>> getSummary(
            @PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails user) {
        SummaryResponse summary = summaryService.getSummary(documentId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @Operation(summary = "요약 삭제 (재생성용)")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteSummary(
            @PathVariable Long documentId,
            @AuthenticationPrincipal CustomUserDetails user) {
        summaryService.deleteSummary(documentId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
