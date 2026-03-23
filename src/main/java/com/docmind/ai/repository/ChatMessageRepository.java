package com.docmind.ai.repository;

import com.docmind.ai.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * ChatMessage 리포지토리.
 *
 * 인덱스 활용:
 *   - findAllByChatSessionId : idx_chat_message_session (chat_session_id, created_at)
 *     → 세션별 메시지 시간순 페이징 (가장 빈번)
 *
 * N+1 방지:
 *   - 메시지 목록 조회 시 ChatSession을 JOIN FETCH하지 않음.
 *     (동일 세션 컨텍스트에서 이미 로딩됨, 불필요한 JOIN 회피)
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 세션별 메시지 페이징 조회 (시간 순서 보장).
     * idx_chat_message_session (chat_session_id, created_at) 복합 인덱스 활용.
     * Pageable의 sort에 "createdAt" ASC 사용 권장.
     */
    Page<ChatMessage> findAllByChatSessionId(Long chatSessionId, Pageable pageable);

    /**
     * 세션별 전체 메시지 목록 조회 (대화 컨텍스트 조립용).
     * Claude API 호출 시 이전 대화 이력 전달에 사용.
     * idx_chat_message_session 활용.
     */
    List<ChatMessage> findAllByChatSessionIdOrderByCreatedAt(Long chatSessionId);

    /**
     * 세션별 최근 N개 메시지 조회 (대화 컨텍스트 슬라이딩 윈도우).
     * 긴 대화에서 컨텍스트 토큰 초과 방지를 위해 최근 메시지만 사용.
     */
    @Query("""
        SELECT cm FROM ChatMessage cm
        WHERE cm.chatSession.id = :sessionId
        ORDER BY cm.createdAt DESC
        LIMIT :limit
        """)
    List<ChatMessage> findRecentByChatSessionId(
        @Param("sessionId") Long sessionId,
        @Param("limit") int limit
    );

    /**
     * 세션 메시지 수 집계.
     */
    long countByChatSessionId(Long chatSessionId);

    /**
     * 세션 메시지 전체 삭제 (세션 삭제 시 ON DELETE CASCADE로 처리되나,
     * 명시적 벌크 DELETE가 필요한 경우를 위해 제공).
     */
    @Modifying
    @Query("DELETE FROM ChatMessage cm WHERE cm.chatSession.id = :sessionId")
    void deleteAllByChatSessionId(@Param("sessionId") Long sessionId);
}
