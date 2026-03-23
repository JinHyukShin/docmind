package com.docmind.ai.dto;

import com.docmind.ai.entity.ChatSession;

import java.time.Instant;
import java.util.List;

public record ChatSessionResponse(
        Long id,
        String title,
        List<Long> documentIds,
        Instant createdAt,
        Instant updatedAt
) {
    public static ChatSessionResponse from(ChatSession session, List<Long> documentIds) {
        return new ChatSessionResponse(
                session.getId(),
                session.getTitle(),
                documentIds,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }
}
