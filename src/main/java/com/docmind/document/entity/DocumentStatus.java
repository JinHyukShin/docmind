package com.docmind.document.entity;

/**
 * 문서 처리 파이프라인 상태.
 *
 * 전이 순서:
 *   UPLOADED → PARSING → CHUNKING → EMBEDDING → READY
 *                                               ↓ (오류 시)
 *                                              FAILED
 */
public enum DocumentStatus {

    /** 파일 업로드 완료, 파싱 대기 중 */
    UPLOADED,

    /** Apache Tika로 텍스트 추출 중 */
    PARSING,

    /** 텍스트를 청크로 분할 중 */
    CHUNKING,

    /** 임베딩 API 호출 중 (Voyage AI) */
    EMBEDDING,

    /** 모든 처리 완료, 검색 가능 상태 */
    READY,

    /** 파이프라인 처리 중 오류 발생 */
    FAILED
}
