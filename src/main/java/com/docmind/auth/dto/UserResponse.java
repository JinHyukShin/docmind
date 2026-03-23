package com.docmind.auth.dto;

import com.docmind.auth.entity.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String name,
        String role,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
