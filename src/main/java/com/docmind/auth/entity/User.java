package com.docmind.auth.entity;

import com.docmind.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * app_user 테이블 매핑 엔티티.
 *
 * 인덱스 전략 (DDL 기반):
 *   - email UNIQUE 제약 → 별도 인덱스 없음 (UNIQUE 제약이 인덱스를 생성함)
 *
 * setter 미사용: 변경은 도메인 메서드를 통해서만 허용.
 */
@Entity
@Table(name = "app_user")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private boolean enabled;

    protected User() {
        // JPA 전용
    }

    private User(String email, String passwordHash, String name, String role) {
        this.email        = email;
        this.passwordHash = passwordHash;
        this.name         = name;
        this.role         = role;
        this.enabled      = true;
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    public static User create(String email, String passwordHash, String name) {
        return new User(email, passwordHash, name, "USER");
    }

    public static User createAdmin(String email, String passwordHash, String name) {
        return new User(email, passwordHash, name, "ADMIN");
    }

    // -------------------------------------------------------------------------
    // Domain methods
    // -------------------------------------------------------------------------

    /** 계정 비활성화 (탈퇴 처리) */
    public void disable() {
        this.enabled = false;
    }

    /** 계정 활성화 */
    public void enable() {
        this.enabled = true;
    }

    /** 비밀번호 변경 */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /** 이름 변경 */
    public void changeName(String newName) {
        this.name = newName;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
