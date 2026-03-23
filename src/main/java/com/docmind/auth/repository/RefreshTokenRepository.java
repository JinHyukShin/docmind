package com.docmind.auth.repository;

import com.docmind.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * RefreshToken 리포지토리.
 *
 * 인덱스 활용:
 *   - findByToken        : token UNIQUE 제약 인덱스 → 토큰 유효성 검증 최빈 경로
 *   - deleteByUserId     : idx_refresh_token_user_id → 로그아웃 일괄 삭제
 *   - deleteAllExpired   : idx_refresh_token_expires_at → 만료 배치 정리
 *
 * @Modifying + @Query: Spring Data의 deleteBy~ 파생 메서드는 내부적으로
 *   SELECT → DELETE N건 루프(N+1 DELETE 문제) 발생 가능.
 *   벌크 DELETE JPQL로 직접 지정하여 단일 쿼리 보장.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * token 값으로 RefreshToken 단건 조회.
     * token UNIQUE 인덱스 → Index Scan.
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * 특정 사용자의 모든 RefreshToken 삭제 (로그아웃).
     * 벌크 DELETE: N건 개별 DELETE 대신 단일 쿼리.
     * idx_refresh_token_user_id → Range Scan.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 만료된 RefreshToken 일괄 삭제 (배치 정리).
     * 벌크 DELETE: idx_refresh_token_expires_at Range Scan으로 만료 범위 최소화.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    void deleteAllExpiredBefore(@Param("now") Instant now);
}
