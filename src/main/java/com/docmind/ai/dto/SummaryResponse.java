package com.docmind.ai.dto;

import com.docmind.ai.entity.DocumentSummary;

import java.time.Instant;

public record SummaryResponse(
        Long id,
        Long documentId,
        String content,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Instant createdAt
) {
    public static SummaryResponse from(DocumentSummary summary) {
        return new SummaryResponse(
                summary.getId(),
                summary.getDocumentId(),
                summary.getContent(),
                summary.getModel(),
                summary.getInputTokens(),
                summary.getOutputTokens(),
                summary.getCreatedAt()
        );
    }
}
