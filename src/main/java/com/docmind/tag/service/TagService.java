package com.docmind.tag.service;

import com.docmind.auth.entity.User;
import com.docmind.auth.repository.UserRepository;
import com.docmind.document.entity.Document;
import com.docmind.document.repository.DocumentRepository;
import com.docmind.global.common.PageResponse;
import com.docmind.global.exception.BusinessException;
import com.docmind.global.exception.ErrorCode;
import com.docmind.tag.dto.TagResponse;
import com.docmind.tag.entity.DocumentTag;
import com.docmind.tag.entity.Tag;
import com.docmind.tag.repository.DocumentTagRepository;
import com.docmind.tag.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 태그 관리 서비스.
 *
 * 주요 기능:
 *   - 태그 생성 (사용자별 고유 이름 보장)
 *   - 사용자 태그 목록 조회
 *   - 문서에 태그 추가 / 제거
 *   - 태그별 문서 목록 조회
 *
 * 문서 접근 시 userId 체크: findByIdAndUserId()로 소유권을 반드시 확인.
 * 태그 접근 시 userId 체크: tag.getUserId() 비교로 소유권 확인.
 */
@Service
@Transactional
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tagRepository;
    private final DocumentTagRepository documentTagRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    public TagService(TagRepository tagRepository,
                      DocumentTagRepository documentTagRepository,
                      DocumentRepository documentRepository,
                      UserRepository userRepository) {
        this.tagRepository = tagRepository;
        this.documentTagRepository = documentTagRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // 태그 CRUD
    // -------------------------------------------------------------------------

    /**
     * 태그를 생성한다.
     *
     * @throws BusinessException DUPLICATE - 동일 사용자의 동일 이름 태그가 이미 존재할 때
     */
    public TagResponse createTag(String name, Long userId) {
        if (tagRepository.existsByNameAndUserId(name, userId)) {
            throw new BusinessException(ErrorCode.DUPLICATE,
                    "Tag with name '" + name + "' already exists");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));

        Tag tag = Tag.create(name, user);
        tag = tagRepository.save(tag);

        log.info("Tag created: id={}, name='{}', userId={}", tag.getId(), name, userId);
        return TagResponse.from(tag);
    }

    /**
     * 사용자의 태그 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public List<TagResponse> getTags(Long userId) {
        return tagRepository.findAllByUserId(userId).stream()
                .map(TagResponse::from)
                .toList();
    }

    // -------------------------------------------------------------------------
    // 문서-태그 연결 관리
    // -------------------------------------------------------------------------

    /**
     * 문서에 태그를 추가한다.
     *
     * @throws BusinessException NOT_FOUND   - 문서 또는 태그가 존재하지 않거나, 소유권 불일치
     * @throws BusinessException DUPLICATE   - 이미 해당 문서에 태그가 추가된 경우
     * @throws BusinessException FORBIDDEN   - 태그가 요청 사용자 소유가 아닌 경우
     */
    public void addTagToDocument(Long documentId, Long tagId, Long userId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Document not found: id=" + documentId));

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Tag not found: id=" + tagId));

        // 태그 소유권 확인
        if (!tag.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Tag does not belong to the current user");
        }

        // 중복 방지
        if (documentTagRepository.existsByDocumentIdAndTagId(documentId, tagId)) {
            throw new BusinessException(ErrorCode.DUPLICATE,
                    "Tag is already added to the document");
        }

        DocumentTag documentTag = DocumentTag.create(document, tag);
        documentTagRepository.save(documentTag);

        log.info("Tag added: documentId={}, tagId={}, userId={}", documentId, tagId, userId);
    }

    /**
     * 문서에서 태그를 제거한다.
     *
     * @throws BusinessException NOT_FOUND - 문서가 존재하지 않거나, 소유권 불일치
     * @throws BusinessException FORBIDDEN - 태그가 요청 사용자 소유가 아닌 경우
     */
    public void removeTagFromDocument(Long documentId, Long tagId, Long userId) {
        // 문서 소유권 확인
        documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Document not found: id=" + documentId));

        // 태그 소유권 확인
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Tag not found: id=" + tagId));

        if (!tag.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Tag does not belong to the current user");
        }

        documentTagRepository.deleteByDocumentIdAndTagId(documentId, tagId);

        log.info("Tag removed: documentId={}, tagId={}, userId={}", documentId, tagId, userId);
    }

    // -------------------------------------------------------------------------
    // 태그별 문서 조회
    // -------------------------------------------------------------------------

    /**
     * 특정 태그가 붙은 문서 목록을 페이징 조회한다.
     *
     * @throws BusinessException NOT_FOUND - 태그가 존재하지 않거나, 소유권 불일치
     * @throws BusinessException FORBIDDEN - 태그가 요청 사용자 소유가 아닌 경우
     */
    @Transactional(readOnly = true)
    public PageResponse<com.docmind.document.dto.DocumentResponse> getDocumentsByTag(
            Long tagId, Long userId, Pageable pageable) {

        // 태그 소유권 확인
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Tag not found: id=" + tagId));

        if (!tag.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Tag does not belong to the current user");
        }

        // DocumentTag 페이징 조회 (N+1 방지: @EntityGraph(document) 활용)
        Page<com.docmind.document.dto.DocumentResponse> page =
                documentTagRepository.findAllByTagId(tagId, pageable)
                        .map(dt -> com.docmind.document.dto.DocumentResponse.from(dt.getDocument()));

        return PageResponse.from(page);
    }
}
