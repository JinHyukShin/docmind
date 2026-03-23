package com.docmind.ai.service;

import com.docmind.ai.dto.SummaryResponse;
import com.docmind.ai.entity.DocumentChunk;
import com.docmind.ai.entity.DocumentSummary;
import com.docmind.ai.repository.DocumentChunkRepository;
import com.docmind.ai.repository.DocumentSummaryRepository;
import com.docmind.document.entity.Document;
import com.docmind.document.entity.DocumentStatus;
import com.docmind.document.repository.DocumentRepository;
import com.docmind.global.exception.BusinessException;
import com.docmind.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * AI 요약 서비스.
 *
 * - 문서의 처음 N개 청크를 조립하여 요약 요청
 * - SSE 스트리밍으로 실시간 응답
 * - 완료 후 DocumentSummary 엔티티 저장
 * - 이미 요약이 있으면 캐시 반환
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    /** 요약에 사용할 최대 청크 수 */
    private static final int MAX_CHUNKS_FOR_SUMMARY = 20;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentSummaryRepository summaryRepository;
    private final ClaudeApiService claudeApiService;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SummaryService(DocumentRepository documentRepository,
                          DocumentChunkRepository chunkRepository,
                          DocumentSummaryRepository summaryRepository,
                          ClaudeApiService claudeApiService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.summaryRepository = summaryRepository;
        this.claudeApiService = claudeApiService;
    }

    /**
     * 요약 생성 (SSE 스트리밍).
     * 이미 요약이 존재하면 캐시된 요약을 SSE로 즉시 반환한다.
     */
    public SseEmitter generateSummary(Long documentId, Long userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Document not found"));

        if (document.getStatus() != DocumentStatus.READY) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "Document is not ready for summarization. Current status: " + document.getStatus());
        }

        // 캐시 확인: 이미 요약이 있으면 즉시 반환
        Optional<DocumentSummary> existingSummary = summaryRepository.findByDocumentId(documentId);
        if (existingSummary.isPresent()) {
            SseEmitter emitter = new SseEmitter(30_000L);
            executor.execute(() -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("summary")
                            .data(new SseData(existingSummary.get().getContent(), true, existingSummary.get().getId())));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            });
            return emitter;
        }

        // 청크 조립
        List<DocumentChunk> chunks = chunkRepository.findAllByDocumentIdOrderByChunkIndex(documentId);
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "No chunks found for document");
        }

        String documentText = chunks.stream()
                .limit(MAX_CHUNKS_FOR_SUMMARY)
                .map(DocumentChunk::getContent)
                .collect(Collectors.joining("\n\n"));

        // SSE 스트리밍으로 요약 생성
        SseEmitter emitter = new SseEmitter(120_000L);
        StringBuilder fullSummary = new StringBuilder();

        executor.execute(() -> {
            try {
                claudeApiService.streamChatInternal(
                        """
                        당신은 문서 요약 전문가입니다.
                        주어진 문서를 한국어로 3~5문단으로 요약하세요.
                        핵심 내용, 주요 결론, 중요한 수치/사실을 포함하세요.
                        """,
                        documentText,
                        // onText
                        text -> {
                            fullSummary.append(text);
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("summary")
                                        .data(new SseData(text, false, null)));
                            } catch (Exception e) {
                                log.warn("Failed to send summary SSE event", e);
                            }
                        },
                        // onComplete
                        () -> {
                            try {
                                // 요약 저장
                                DocumentSummary summary = saveSummary(document, fullSummary.toString());
                                emitter.send(SseEmitter.event()
                                        .name("summary")
                                        .data(new SseData("", true, summary.getId())));
                                emitter.complete();
                            } catch (Exception e) {
                                log.error("Failed to save summary", e);
                                emitter.completeWithError(e);
                            }
                        },
                        // onError
                        error -> {
                            log.error("Summary generation failed", error);
                            emitter.completeWithError(error);
                        }
                );
            } catch (Exception e) {
                log.error("Summary streaming failed", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 저장된 요약 조회.
     */
    @Transactional(readOnly = true)
    public SummaryResponse getSummary(Long documentId, Long userId) {
        // 문서 소유권 검증
        documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Document not found"));

        DocumentSummary summary = summaryRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Summary not found"));

        return SummaryResponse.from(summary);
    }

    /**
     * 요약 삭제 (재생성용).
     */
    @Transactional
    public void deleteSummary(Long documentId, Long userId) {
        documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Document not found"));

        summaryRepository.deleteByDocumentId(documentId);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    @Transactional
    protected DocumentSummary saveSummary(Document document, String content) {
        // 기존 요약이 있으면 업데이트
        Optional<DocumentSummary> existing = summaryRepository.findByDocumentId(document.getId());
        if (existing.isPresent()) {
            existing.get().update(content, claudeApiService.getModel(), null, null);
            return summaryRepository.save(existing.get());
        }

        DocumentSummary summary = DocumentSummary.create(
                document, content, claudeApiService.getModel(), null, null);
        return summaryRepository.save(summary);
    }

    // SSE 데이터 DTO
    record SseData(String content, boolean done, Long summaryId) {}
}
