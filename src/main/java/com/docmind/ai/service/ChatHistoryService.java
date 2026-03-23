package com.docmind.ai.service;

import com.docmind.ai.dto.ChatMessageResponse;
import com.docmind.ai.dto.ChatSessionCreateRequest;
import com.docmind.ai.dto.ChatSessionDetailResponse;
import com.docmind.ai.dto.ChatSessionResponse;
import com.docmind.ai.entity.ChatMessage;
import com.docmind.ai.entity.ChatSession;
import com.docmind.ai.entity.ChatSessionDocument;
import com.docmind.ai.repository.ChatMessageRepository;
import com.docmind.ai.repository.ChatSessionDocumentRepository;
import com.docmind.ai.repository.ChatSessionRepository;
import com.docmind.auth.entity.User;
import com.docmind.auth.repository.UserRepository;
import com.docmind.document.entity.Document;
import com.docmind.document.entity.DocumentStatus;
import com.docmind.document.repository.DocumentRepository;
import com.docmind.global.exception.BusinessException;
import com.docmind.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 채팅 이력 관리 서비스.
 */
@Service
public class ChatHistoryService {

    private final ChatSessionRepository sessionRepository;
    private final ChatSessionDocumentRepository sessionDocumentRepository;
    private final ChatMessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    public ChatHistoryService(ChatSessionRepository sessionRepository,
                              ChatSessionDocumentRepository sessionDocumentRepository,
                              ChatMessageRepository messageRepository,
                              DocumentRepository documentRepository,
                              UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.sessionDocumentRepository = sessionDocumentRepository;
        this.messageRepository = messageRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    /**
     * 채팅 세션 생성.
     * 지정된 문서들이 READY 상태인지 검증하고, 세션-문서 연결을 생성한다.
     */
    @Transactional
    public ChatSessionResponse createSession(Long userId, ChatSessionCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

        // 문서 존재 여부 및 소유권, 상태 검증
        List<Document> documents = request.documentIds().stream()
                .map(docId -> documentRepository.findByIdAndUserId(docId, userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                                "Document not found: " + docId)))
                .toList();

        for (Document doc : documents) {
            if (doc.getStatus() != DocumentStatus.READY) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "Document is not ready: " + doc.getId() + " (status: " + doc.getStatus() + ")");
            }
        }

        // 세션 생성
        String title = request.title() != null ? request.title()
                : documents.getFirst().getTitle() + " Q&A";
        ChatSession session = ChatSession.create(user, title);
        session = sessionRepository.save(session);

        // 세션-문서 연결
        for (Document doc : documents) {
            sessionDocumentRepository.save(ChatSessionDocument.create(session, doc));
        }

        return ChatSessionResponse.from(session, request.documentIds());
    }

    /**
     * 사용자 세션 목록 조회.
     */
    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getSessions(Long userId) {
        Page<ChatSession> sessions = sessionRepository.findAllByUserId(
                userId,
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")));

        return sessions.getContent().stream()
                .map(session -> {
                    List<Long> docIds = sessionDocumentRepository.findDocumentIdsByChatSessionId(session.getId());
                    return ChatSessionResponse.from(session, docIds);
                })
                .toList();
    }

    /**
     * 세션 상세 조회 (메시지 포함).
     */
    @Transactional(readOnly = true)
    public ChatSessionDetailResponse getSessionDetail(Long sessionId, Long userId) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Chat session not found"));

        List<Long> docIds = sessionDocumentRepository.findDocumentIdsByChatSessionId(sessionId);

        Page<ChatMessage> messagesPage = messageRepository.findAllByChatSessionId(
                sessionId,
                PageRequest.of(0, 200, Sort.by(Sort.Direction.ASC, "createdAt")));

        List<ChatMessageResponse> messages = messagesPage.getContent().stream()
                .map(ChatMessageResponse::from)
                .toList();

        return ChatSessionDetailResponse.from(session, docIds, messages);
    }

    /**
     * 세션 삭제.
     */
    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Chat session not found"));

        messageRepository.deleteAllByChatSessionId(sessionId);
        sessionDocumentRepository.deleteAllByChatSessionId(sessionId);
        sessionRepository.delete(session);
    }

    /**
     * 세션 소유권 검증 후 반환.
     */
    @Transactional(readOnly = true)
    public ChatSession getSessionForUser(Long sessionId, Long userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Chat session not found"));
    }

    /**
     * 세션에 연결된 문서 ID 목록 조회.
     */
    @Transactional(readOnly = true)
    public List<Long> getDocumentIds(Long sessionId) {
        return sessionDocumentRepository.findDocumentIdsByChatSessionId(sessionId);
    }
}
