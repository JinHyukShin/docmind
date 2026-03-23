package com.docmind.ai.dto;

import com.docmind.ai.entity.ChatSession;

import java.time.Instant;
import java.util.List;

public record ChatSessionDetailResponse(
        Long id,
        String title,
        List<Long> documentIds,
        List<ChatMessageResponse> messages,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChatSessionDetailResponse from(ChatSession session,
                                                  List<Long> documentIds,
                                                  List<ChatMessageResponse> messages) {
        return new ChatSessionDetailResponse(
                session.getId(),
                session.getTitle(),
                documentIds,
                messages,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
