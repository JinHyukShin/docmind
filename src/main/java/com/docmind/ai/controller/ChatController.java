package com.docmind.ai.controller;

import com.docmind.ai.dto.ChatMessageResponse;
import com.docmind.ai.dto.ChatRequest;
import com.docmind.ai.dto.ChatSessionCreateRequest;
import com.docmind.ai.dto.ChatSessionDetailResponse;
import com.docmind.ai.dto.ChatSessionResponse;
import com.docmind.ai.entity.ChatSession;
import com.docmind.ai.service.ChatHistoryService;
import com.docmind.ai.service.RagService;
import com.docmind.global.common.ApiResponse;
import com.docmind.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * RAG Q&A 채팅 컨트롤러.
 *
 * POST   /api/v1/chat/sessions                  -- 세션 생성 (201)
 * GET    /api/v1/chat/sessions                  -- 세션 목록
 * GET    /api/v1/chat/sessions/{id}             -- 세션 상세 (메시지 포함)
 * DELETE /api/v1/chat/sessions/{id}             -- 세션 삭제
 * POST   /api/v1/chat/sessions/{id}/messages    -- 질문 전송 (SSE 스트리밍)
 */
@Tag(name = "Chat", description = "RAG Q&A 채팅 API")
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatHistoryService chatHistoryService;
    private final RagService ragService;

    public ChatController(ChatHistoryService chatHistoryService,
                          RagService ragService) {
        this.chatHistoryService = chatHistoryService;
        this.ragService = ragService;
    }

    @Operation(summary = "채팅 세션 생성")
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> createSession(
            @Valid @RequestBody ChatSessionCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        ChatSessionResponse session = chatHistoryService.createSession(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(session));
    }

    @Operation(summary = "채팅 세션 목록 조회")
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<ChatSessionResponse>>> getSessions(
            @AuthenticationPrincipal CustomUserDetails user) {
        List<ChatSessionResponse> sessions = chatHistoryService.getSessions(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @Operation(summary = "채팅 세션 상세 조회 (메시지 포함)")
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<ChatSessionDetailResponse>> getSessionDetail(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails user) {
        ChatSessionDetailResponse detail = chatHistoryService.getSessionDetail(sessionId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @Operation(summary = "채팅 세션 삭제")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal CustomUserDetails user) {
        chatHistoryService.deleteSession(sessionId, user.getUserId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "질문 전송 (SSE 스트리밍 응답)")
    @PostMapping(value = "/sessions/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {

        // 세션 소유권 검증
        ChatSession session = chatHistoryService.getSessionForUser(sessionId, user.getUserId());

        // RAG 파이프라인 실행 (SSE 스트리밍)
        return ragService.askQuestion(session, request.content(), user.getUserId());
    }
}
