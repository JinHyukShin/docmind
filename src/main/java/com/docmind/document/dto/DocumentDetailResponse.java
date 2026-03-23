package com.docmind.document.dto;

import com.docmind.document.entity.Document;

import java.time.Instant;

public record DocumentDetailResponse(
        Long id,
        String title,
        String originalFileName,
        Long fileSize,
        String mimeType,
        String status,
        Integer pageCount,
        Integer textLength,
        Integer chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentDetailResponse from(Document document) {
        return new DocumentDetailResponse(
                document.getId(),
                document.getTitle(),
                document.getOriginalFileName(),
                document.getFileSize(),
                document.getMimeType(),
                document.getStatus().name(),
                document.getPageCount(),
                document.getTextLength(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
