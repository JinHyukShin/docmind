package com.docmind.auth.repository;

import com.docmind.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User 리포지토리.
 *
 * findByEmail : email UNIQUE 제약 인덱스 → 로그인 경로 단건 조회.
 * existsByEmail: Spring Data JPA가 EXISTS 쿼리로 변환 → COUNT(*) 보다 효율적.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 이메일로 사용자 단건 조회.
     * email UNIQUE 인덱스 → Index Scan, 비용 최소.
     */
    Optional<User> findByEmail(String email);

    /**
     * 이메일 존재 여부 확인 (회원가입 중복 검사).
     * Spring Data JPA가 EXISTS 쿼리로 변환 → COUNT(*) 보다 효율적.
     */
    boolean existsByEmail(String email);
}
