-- =============================================================================
-- V2__initial_data.sql
-- DocMind 초기 데이터 삽입
-- =============================================================================

-- 관리자 계정 (admin@docmind.com)
-- password_hash: BCrypt 해시 (평문: Admin1234!)
INSERT INTO app_user (email, password_hash, name, role, enabled)
VALUES (
    'admin@docmind.com',
    '$2a$12$7Bz5P9QkL8mNxRvWuT3qHeKjY6FdCsA0oIpXnMgEwZlVb4hO1rJui',
    'DocMind Admin',
    'ADMIN',
    TRUE
);
