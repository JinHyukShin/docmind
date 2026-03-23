package com.docmind.document.event;

/**
 * 문서 업로드 완료 이벤트.
 * 트랜잭션 커밋 후 비동기 파싱 파이프라인을 트리거한다.
 */
public record DocumentUploadedEvent(Long documentId) {
}
