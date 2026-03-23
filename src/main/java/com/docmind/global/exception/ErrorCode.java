package com.docmind.global.exception;

public enum ErrorCode {

    // --- Common ---
    INVALID_INPUT(400, "INVALID_INPUT", "Invalid input"),
    UNAUTHORIZED(401, "UNAUTHORIZED", "Unauthorized"),
    FORBIDDEN(403, "FORBIDDEN", "Access denied"),
    NOT_FOUND(404, "NOT_FOUND", "Resource not found"),
    DUPLICATE(409, "DUPLICATE", "Resource already exists"),
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "Internal server error"),

    // --- Document ---
    FILE_TOO_LARGE(400, "FILE_TOO_LARGE", "File size exceeds the maximum limit"),
    UNSUPPORTED_FILE_TYPE(400, "UNSUPPORTED_FILE_TYPE", "File type is not supported"),
    DOCUMENT_PARSING_FAILED(500, "DOCUMENT_PARSING_FAILED", "Document parsing failed"),

    // --- AI ---
    AI_SERVICE_ERROR(502, "AI_SERVICE_ERROR", "AI service is temporarily unavailable"),
    EMBEDDING_ERROR(502, "EMBEDDING_ERROR", "Embedding generation failed"),

    // --- Search ---
    INVALID_SEARCH_TYPE(400, "INVALID_SEARCH_TYPE", "Invalid search type"),

    // --- Tag ---
    TAG_NOT_FOUND(404, "TAG_NOT_FOUND", "Tag not found"),
    TAG_ALREADY_EXISTS(409, "TAG_ALREADY_EXISTS", "Tag already exists");

    private final int httpStatus;
    private final String code;
    private final String message;

    ErrorCode(int httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
