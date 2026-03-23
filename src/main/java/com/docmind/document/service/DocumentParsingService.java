package com.docmind.document.service;

import com.docmind.ai.entity.DocumentChunk;
import com.docmind.ai.repository.DocumentChunkRepository;
import com.docmind.ai.service.ChunkingService;
import com.docmind.ai.service.ChunkingService.TextChunk;
import com.docmind.ai.service.EmbeddingService;
import com.docmind.document.entity.Document;
import com.docmind.document.entity.DocumentStatus;
import com.docmind.document.event.DocumentUploadedEvent;
import com.docmind.document.repository.DocumentRepository;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.InputStream;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 문서 파싱 서비스.
 *
 * @Async + Virtual Thread에서 실행되며,
 * MinIO 다운로드 -> Apache Tika 파싱 -> 청킹 -> 임베딩 -> pgvector 저장 파이프라인을 처리한다.
 *
 * 상태 전이: UPLOADED -> PARSING -> CHUNKING -> EMBEDDING -> READY
 */
@Service
public class DocumentParsingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParsingService.class);

    private static final int CHUNK_SIZE = 512;
    private static final int CHUNK_OVERLAP = 50;

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository chunkRepository;

    public DocumentParsingService(DocumentRepository documentRepository,
                                  FileStorageService fileStorageService,
                                  ChunkingService chunkingService,
                                  EmbeddingService embeddingService,
                                  DocumentChunkRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
    }

    /**
     * 트랜잭션 커밋 후 문서 파싱 이벤트를 수신하여 비동기 파이프라인을 시작한다.
     * DocumentService.upload() 트랜잭션이 커밋된 후에만 실행되므로
     * Document 엔티티가 확실히 DB에 존재한다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        processDocument(event.documentId());
    }

    /**
     * 비동기로 문서 파싱 + 청킹 + 임베딩 파이프라인을 실행한다.
     * Virtual Thread에서 실행되므로 블로킹 I/O가 허용된다.
     *
     * 파이프라인:
     *   1. 상태를 PARSING으로 변경
     *   2. MinIO에서 파일 다운로드
     *   3. Apache Tika로 텍스트 추출
     *   4. 메타데이터(pageCount, textLength) 갱신
     *   5. 상태를 CHUNKING으로 변경 → 텍스트 청킹
     *   6. 상태를 EMBEDDING으로 변경 → 배치 임베딩 생성
     *   7. 청크 + 벡터 pgvector 저장
     *   8. 상태를 READY로 변경
     *   9. 에러 시 FAILED + errorMessage 저장
     */
    @Transactional
    public void processDocument(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElse(null);

        if (document == null) {
            log.warn("Document not found for parsing: id={}", documentId);
            return;
        }

        try {
            // 1. 상태를 PARSING으로 변경
            document.updateStatus(DocumentStatus.PARSING);
            documentRepository.saveAndFlush(document);

            // 2. MinIO에서 파일 다운로드 + 3. Tika 파싱
            String extractedText;
            Metadata metadata = new Metadata();
            try (InputStream inputStream = fileStorageService.download(document.getStoredFilePath())) {
                AutoDetectParser parser = new AutoDetectParser();
                BodyContentHandler handler = new BodyContentHandler(-1); // 제한 없음
                parser.parse(inputStream, handler, metadata);
                extractedText = handler.toString();
            }

            // 4. 메타데이터 갱신
            int pageCount = parsePageCount(metadata);
            int textLength = extractedText != null ? extractedText.length() : 0;

            document.completeParsing(pageCount, textLength);
            documentRepository.saveAndFlush(document);

            log.info("Document parsed successfully: id={}, pages={}, textLength={}",
                    documentId, pageCount, textLength);

            // 5. 텍스트 청킹
            // 상태는 이미 completeParsing()에서 CHUNKING으로 설정됨
            List<TextChunk> chunks = chunkingService.chunk(extractedText, CHUNK_SIZE, CHUNK_OVERLAP);

            log.info("Document chunked: id={}, chunks={}", documentId, chunks.size());

            // 6. 상태를 EMBEDDING으로 변경
            document.updateStatus(DocumentStatus.EMBEDDING);
            documentRepository.saveAndFlush(document);

            // 7. 배치 임베딩 생성
            List<String> chunkTexts = chunks.stream()
                    .map(TextChunk::content)
                    .toList();
            List<float[]> embeddings = embeddingService.batchEmbed(chunkTexts);

            log.info("Document embedded: id={}, embeddings={}", documentId, embeddings.size());

            // 8. 청크 + 벡터 저장
            List<DocumentChunk> chunkEntities = IntStream.range(0, chunks.size())
                    .mapToObj(i -> DocumentChunk.create(
                            document,
                            i,
                            chunks.get(i).content(),
                            chunks.get(i).tokenCount(),
                            embeddings.get(i),
                            chunks.get(i).pageStart(),
                            chunks.get(i).pageEnd()
                    ))
                    .toList();
            chunkRepository.saveAll(chunkEntities);

            // 9. 상태를 READY로 변경
            document.completeEmbedding(chunks.size());
            documentRepository.save(document);

            log.info("Document processing complete: id={}, status=READY, chunks={}",
                    documentId, chunks.size());

        } catch (Exception e) {
            log.error("Document processing failed: id={}", documentId, e);
            document.markFailed("Processing failed: " + e.getMessage());
            documentRepository.save(document);
        }
    }

    private int parsePageCount(Metadata metadata) {
        String pageCountStr = metadata.get("xmpTPg:NPages");
        if (pageCountStr == null) {
            pageCountStr = metadata.get("meta:page-count");
        }
        if (pageCountStr == null) {
            pageCountStr = metadata.get("Page-Count");
        }
        if (pageCountStr != null) {
            try {
                return Integer.parseInt(pageCountStr);
            } catch (NumberFormatException ignored) {
                // 파싱 불가 시 0 반환
            }
        }
        return 0;
    }
}
