package com.docmind.ai.repository;

import com.docmind.ai.entity.ChatSessionDocument;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * ChatSessionDocument 리포지토리 (세션-문서 연결).
 *
 * 인덱스 활용:
 *   - findAllByChatSessionId : (chat_session_id, document_id) UNIQUE 제약 인덱스
 *     → chat_session_id 기준 Range Scan
 *
 * N+1 방지:
 *   - @EntityGraph로 document를 LEFT JOIN FETCH하여 한 번의 쿼리로 로딩.
 */
public interface ChatSessionDocumentRepository extends JpaRepository<ChatSessionDocument, Long> {

    /**
     * 세션의 연결 문서 목록 조회 (Document FETCH JOIN으로 N+1 방지).
     * (chat_session_id, document_id) UNIQUE 인덱스 Range Scan.
     */
    @EntityGraph(attributePaths = {"document"})
    List<ChatSessionDocument> findAllByChatSessionId(Long chatSessionId);

    /**
     * 세션의 문서 ID 목록만 조회 (pgvector 검색 파라미터용).
     */
    @Query("SELECT csd.document.id FROM ChatSessionDocument csd WHERE csd.chatSession.id = :sessionId")
    List<Long> findDocumentIdsByChatSessionId(@Param("sessionId") Long sessionId);

    /**
     * 특정 세션-문서 연결 존재 여부 확인.
     */
    boolean existsByChatSessionIdAndDocumentId(Long chatSessionId, Long documentId);

    /**
     * 세션의 모든 문서 연결 삭제.
     * 벌크 DELETE: chat_session_id Range Scan.
     */
    @Modifying
    @Query("DELETE FROM ChatSessionDocument csd WHERE csd.chatSession.id = :sessionId")
    void deleteAllByChatSessionId(@Param("sessionId") Long sessionId);
}
