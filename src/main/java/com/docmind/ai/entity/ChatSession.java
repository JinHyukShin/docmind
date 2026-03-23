package com.docmind.ai.entity;

import com.docmind.auth.entity.User;
import com.docmind.global.common.BaseEntity;
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

/**
 * chat_session 테이블 매핑 엔티티.
 *
 * 인덱스 전략 (DDL 기반):
 *   - idx_chat_session_user_id : user_id → 사용자 세션 목록 조회
 *
 * @ManyToOne fetch=LAZY 필수: 세션 목록 조회 시 User 전체 로딩 방지.
 */
@Entity
@Table(
    name = "chat_session",
    indexes = {
        @Index(name = "idx_chat_session_user_id", columnList = "user_id")
    }
)
public class ChatSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY 필수: 세션 목록 조회 시 User 전체를 즉시 로딩하면 N+1 발생.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 200)
    private String title;

    protected ChatSession() {
        // JPA 전용
    }

    private ChatSession(User user, String title) {
        this.user  = user;
        this.title = title;
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    public static ChatSession create(User user, String title) {
        return new ChatSession(user, title);
    }

    // -------------------------------------------------------------------------
    // Domain methods
    // -------------------------------------------------------------------------

    /** 세션 제목 변경 */
    public void changeTitle(String newTitle) {
        this.title = newTitle;
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

    public Long getUserId() {
        return user.getId();
    }

    public String getTitle() {
        return title;
    }
}
