package com.docmind.ai.repository;

import com.docmind.ai.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * ChatSession 리포지토리.
 *
 * 인덱스 활용:
 *   - findAllByUserId    : idx_chat_session_user_id → 사용자 세션 목록 조회
 *   - findByIdAndUserId  : PK(id) + idx_chat_session_user_id → 소유권 검증
 */
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /**
     * 사용자 세션 목록 페이징 조회.
     * idx_chat_session_user_id + Pageable sort 활용.
     * Pageable의 sort에 "createdAt" DESC 사용 권장.
     */
    Page<ChatSession> findAllByUserId(Long userId, Pageable pageable);

    /**
     * 사용자 세션 단건 조회 (소유권 검증 포함).
     * PK 조회 + user_id 필터로 타 사용자 세션 접근 차단.
     */
    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    /**
     * 세션 상세 조회 (User FETCH JOIN으로 N+1 방지).
     * EntityGraph: user 연관을 LEFT JOIN FETCH로 즉시 로딩.
     */
    @EntityGraph(attributePaths = {"user"})
    Optional<ChatSession> findWithUserById(Long id);
}
