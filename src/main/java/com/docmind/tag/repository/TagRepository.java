package com.docmind.tag.repository;

import com.docmind.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Tag 리포지토리.
 *
 * 인덱스 활용:
 *   - findAllByUserId        : idx_tag_user_id → 사용자 태그 목록 조회
 *   - findByNameAndUserId    : (name, user_id) UNIQUE 제약 인덱스 → 중복 검사
 *   - existsByNameAndUserId  : EXISTS 쿼리로 변환 → COUNT(*) 보다 효율적
 */
public interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * 사용자 태그 목록 조회.
     * idx_tag_user_id → Index Scan.
     */
    List<Tag> findAllByUserId(Long userId);

    /**
     * 사용자의 특정 이름 태그 단건 조회.
     * (name, user_id) UNIQUE 인덱스 → Index Scan.
     */
    Optional<Tag> findByNameAndUserId(String name, Long userId);

    /**
     * 사용자의 태그명 존재 여부 확인 (중복 생성 방지).
     */
    boolean existsByNameAndUserId(String name, Long userId);
}
