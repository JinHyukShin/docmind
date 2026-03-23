package com.docmind.ai.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ChatSessionCreateRequest(
        @NotEmpty(message = "At least one document ID is required")
        List<Long> documentIds,
        String title
) {}
