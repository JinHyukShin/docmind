package com.docmind.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * refresh_token 테이블 매핑 엔티티.
 *
 * 인덱스 전략 (DDL 기반):
 *   - token UNIQUE 제약 → 별도 인덱스 없음 (UNIQUE 제약이 인덱스를 생성함)
 *   - idx_refresh_token_user_id    : user_id → deleteByUserId() 일괄 삭제
 *   - idx_refresh_token_expires_at : 만료 배치 정리
 *
 * @ManyToOne fetch=LAZY 필수: User 로딩 N+1 방지.
 */
@Entity
@Table(
    name = "refresh_token",
    indexes = {
        @Index(name = "idx_refresh_token_user_id",    columnList = "user_id"),
        @Index(name = "idx_refresh_token_expires_at", columnList = "expires_at")
    }
)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY 필수: token 유효성 검증 시 User 전체를 즉시 로딩하면
     * 불필요한 JOIN 발생 → 인증 레이어 성능 저하.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
        // JPA 전용
    }

    private RefreshToken(User user, String token, Instant expiresAt) {
        this.user      = user;
        this.token     = token;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public static RefreshToken create(User user, String token, Instant expiresAt) {
        return new RefreshToken(user, token, expiresAt);
    }

    // -------------------------------------------------------------------------
    // Domain methods
    // -------------------------------------------------------------------------

    /** 토큰 만료 여부 확인 */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    /** userId만 필요할 때 LAZY 프록시 초기화 없이 FK 값 직접 접근 */
    public Long getUserId() {
        return user.getId();
    }

    public String getToken() {
        return token;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
