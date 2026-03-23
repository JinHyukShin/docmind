package com.docmind.document.service;

import com.docmind.auth.entity.User;
import com.docmind.auth.repository.UserRepository;
import com.docmind.document.dto.DocumentDetailResponse;
import com.docmind.document.dto.DocumentResponse;
import com.docmind.document.entity.Document;
import com.docmind.document.event.DocumentUploadedEvent;
import com.docmind.document.repository.DocumentRepository;
import com.docmind.global.common.PageResponse;
import com.docmind.global.exception.BusinessException;
import com.docmind.global.exception.ErrorCode;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Service
@Transactional
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );

    private static final Tika TIKA = new Tika();

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentService(DocumentRepository documentRepository,
                           UserRepository userRepository,
                           FileStorageService fileStorageService,
                           ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 문서를 업로드한다.
     *
     * 1. MIME 타입 검증
     * 2. MinIO에 파일 저장
     * 3. Document 엔티티 저장
     * 4. 비동기 파싱 시작
     */
    public DocumentResponse upload(MultipartFile file, String title, String description, Long userId) {
        // 1. MIME 타입 검증
        validateMimeType(file);

        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

        // title이 없으면 원본 파일명 사용
        String documentTitle = (title != null && !title.isBlank()) ? title : file.getOriginalFilename();

        // 2. MinIO에 파일 저장
        String storedPath = fileStorageService.upload(file, userId);

        // 3. Document 엔티티 저장
        Document document = Document.create(
                user,
                documentTitle,
                file.getOriginalFilename(),
                storedPath,
                file.getSize(),
                file.getContentType()
        );
        documentRepository.save(document);

        log.info("Document uploaded: id={}, title={}, size={}", document.getId(), documentTitle, file.getSize());

        // 4. 이벤트 발행 → 트랜잭션 커밋 후 비동기 파싱 시작
        eventPublisher.publishEvent(new DocumentUploadedEvent(document.getId()));

        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> findAll(Long userId, Pageable pageable) {
        Page<DocumentResponse> page = documentRepository.findAllByUserId(userId, pageable)
                .map(DocumentResponse::from);
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse findById(Long documentId, Long userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Document not found"));
        return DocumentDetailResponse.from(document);
    }

    public void delete(Long documentId, Long userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Document not found"));

        // MinIO에서 파일 삭제
        fileStorageService.delete(document.getStoredFilePath());

        // Document 삭제 (cascade로 청크, 벡터도 삭제)
        documentRepository.delete(document);

        log.info("Document deleted: id={}", documentId);
    }

    /**
     * 문서 처리 상태를 조회한다.
     */
    @Transactional(readOnly = true)
    public DocumentResponse getStatus(Long documentId, Long userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Document not found"));
        return DocumentResponse.from(document);
    }

    /**
     * 문서 엔티티를 직접 조회한다 (다운로드용).
     */
    @Transactional(readOnly = true)
    public Document getDocument(Long documentId, Long userId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Document not found"));
    }

    /**
     * MIME 타입을 실제 파일 시그니처(매직 바이트)로 검증한다.
     * Content-Type 헤더를 신뢰하지 않고, Apache Tika로 실제 바이너리를 분석한다.
     */
    private void validateMimeType(MultipartFile file) {
        String declaredType = file.getContentType();
        String detectedType;
        try (InputStream is = file.getInputStream()) {
            detectedType = TIKA.detect(is, file.getOriginalFilename());
        } catch (IOException e) {
            log.warn("Failed to detect MIME type for file: {}", file.getOriginalFilename(), e);
            detectedType = declaredType; // fallback to declared type
        }

        if (detectedType == null || !ALLOWED_MIME_TYPES.contains(detectedType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Unsupported file type: " + detectedType
                            + " (declared: " + declaredType + ")"
                            + ". Allowed types: PDF, DOCX, TXT");
        }
    }
}
