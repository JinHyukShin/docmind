package com.docmind.tag.entity;

import com.docmind.auth.entity.User;
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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * tag 테이블 매핑 엔티티.
 *
 * (name, user_id) UNIQUE 제약: 사용자별 태그명 중복 방지.
 * UNIQUE 제약이 복합 인덱스를 생성하므로 별도 인덱스 불필요.
 * idx_tag_user_id: user_id 단방향 조회를 위한 추가 인덱스.
 *
 * @ManyToOne fetch=LAZY 필수: 태그 목록 조회 시 User 전체 로딩 방지.
 */
@Entity
@Table(
    name = "tag",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tag_name_user",
        columnNames = {"name", "user_id"}
    ),
    indexes = {
        @Index(name = "idx_tag_user_id", columnList = "user_id")
    }
)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tag() {
        // JPA 전용
    }

    private Tag(String name, User user) {
        this.name      = name;
        this.user      = user;
        this.createdAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    public static Tag create(String name, User user) {
        return new Tag(name, user);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
