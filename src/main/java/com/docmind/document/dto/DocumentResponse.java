package com.docmind.document.dto;

import com.docmind.document.entity.Document;

import java.time.Instant;

public record DocumentResponse(
        Long id,
        String title,
        String originalFileName,
        Long fileSize,
        String mimeType,
        String status,
        Integer pageCount,
        Integer chunkCount,
        Instant createdAt
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getOriginalFileName(),
                document.getFileSize(),
                document.getMimeType(),
                document.getStatus().name(),
                document.getPageCount(),
                document.getChunkCount(),
                document.getCreatedAt()
        );
    }
}
