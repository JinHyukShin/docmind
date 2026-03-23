package com.docmind.tag.controller;

import com.docmind.document.dto.DocumentResponse;
import com.docmind.global.common.ApiResponse;
import com.docmind.global.common.PageResponse;
import com.docmind.global.security.CustomUserDetails;
import com.docmind.tag.dto.TagRequest;
import com.docmind.tag.dto.TagResponse;
import com.docmind.tag.service.TagService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 태그 관리 API 컨트롤러.
 *
 * 엔드포인트:
 *   GET    /api/v1/tags                               -- 사용자 태그 목록
 *   POST   /api/v1/tags                               -- 태그 생성
 *   POST   /api/v1/documents/{docId}/tags             -- 문서에 태그 추가
 *   DELETE /api/v1/documents/{docId}/tags/{tagId}     -- 문서에서 태그 제거
 *   GET    /api/v1/tags/{tagId}/documents             -- 태그별 문서 목록
 */
@RestController
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    // -------------------------------------------------------------------------
    // 태그 목록 조회
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/tags
     * 현재 사용자의 태그 목록을 반환한다.
     */
    @GetMapping("/api/v1/tags")
    public ResponseEntity<ApiResponse<List<TagResponse>>> getTags(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<TagResponse> tags = tagService.getTags(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(tags));
    }

    // -------------------------------------------------------------------------
    // 태그 생성
    // -------------------------------------------------------------------------

    /**
     * POST /api/v1/tags
     * 새 태그를 생성한다.
     */
    @PostMapping("/api/v1/tags")
    public ResponseEntity<ApiResponse<TagResponse>> createTag(
            @RequestBody @Valid TagRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TagResponse tag = tagService.createTag(request.name(), userDetails.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tag));
    }

    // -------------------------------------------------------------------------
    // 문서-태그 연결
    // -------------------------------------------------------------------------

    /**
     * POST /api/v1/documents/{docId}/tags
     * 문서에 태그를 추가한다.
     *
     * Request Body: { "name": "태그명" }  -- name으로 tagId를 직접 전달하는 대신
     *               tagId를 body에 담거나 path param으로 받는 방식 선택 가능.
     * 설계서 기준: POST /api/v1/documents/{documentId}/tags 이므로
     *              요청 바디에서 tagId를 받는다.
     */
    @PostMapping("/api/v1/documents/{docId}/tags")
    public ResponseEntity<ApiResponse<Void>> addTagToDocument(
            @PathVariable Long docId,
            @RequestBody TagIdRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        tagService.addTagToDocument(docId, request.tagId(), userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * DELETE /api/v1/documents/{docId}/tags/{tagId}
     * 문서에서 태그를 제거한다.
     */
    @DeleteMapping("/api/v1/documents/{docId}/tags/{tagId}")
    public ResponseEntity<Void> removeTagFromDocument(
            @PathVariable Long docId,
            @PathVariable Long tagId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        tagService.removeTagFromDocument(docId, tagId, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // 태그별 문서 조회
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/tags/{tagId}/documents
     * 특정 태그가 붙은 문서 목록을 페이징 조회한다.
     */
    @GetMapping("/api/v1/tags/{tagId}/documents")
    public ResponseEntity<ApiResponse<PageResponse<DocumentResponse>>> getDocumentsByTag(
            @PathVariable Long tagId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        PageResponse<DocumentResponse> page =
                tagService.getDocumentsByTag(tagId, userDetails.getUserId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    // -------------------------------------------------------------------------
    // Inner DTO: 태그 추가 요청 바디
    // -------------------------------------------------------------------------

    /**
     * 문서에 태그 추가 요청 시 tagId를 담는 바디 DTO.
     */
    record TagIdRequest(Long tagId) {}
}
