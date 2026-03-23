package com.docmind.ai.dto;

import com.docmind.ai.entity.ChatMessage;

import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        String role,
        String content,
        Long[] sourceChunkIds,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getSourceChunkIds(),
                message.getModel(),
                message.getInputTokens(),
                message.getOutputTokens(),
                message.getCreatedAt()
        );
    }
}
