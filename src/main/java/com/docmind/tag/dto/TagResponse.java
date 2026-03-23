package com.docmind.tag.dto;

import com.docmind.tag.entity.Tag;

import java.time.Instant;

/**
 * 태그 응답 DTO.
 */
public record TagResponse(
        Long id,
        String name,
        Instant createdAt
) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getCreatedAt()
        );
    }
}
