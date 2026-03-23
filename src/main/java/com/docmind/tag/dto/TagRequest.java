package com.docmind.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 태그 생성 요청 DTO.
 *
 * name: 태그명 (사용자별 고유, 최대 50자)
 */
public record TagRequest(
        @NotBlank(message = "Tag name must not be blank")
        @Size(max = 50, message = "Tag name must not exceed 50 characters")
        String name
) {}
