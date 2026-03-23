-- =============================================================================
-- V1__init.sql
-- DocMind 초기 스키마 생성
-- PostgreSQL 17 + pgvector
-- =============================================================================

-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================================================
-- app_user: 사용자 계정
-- 조회 패턴: email(로그인), id(PK 조회)
-- email UNIQUE 제약이 이미 인덱스를 생성하므로 별도 인덱스 불필요
-- =============================================================================
CREATE TABLE app_user (
    id              BIGSERIAL    PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_app_user_role CHECK (role IN ('USER', 'ADMIN'))
);

COMMENT ON TABLE  app_user              IS '사용자 계정';
COMMENT ON COLUMN app_user.role         IS 'USER | ADMIN';
COMMENT ON COLUMN app_user.enabled      IS 'false이면 로그인 차단';

-- =============================================================================
-- refresh_token: JWT Refresh Token 저장소
-- 조회 패턴:
--   1) token 값으로 유효성 검증 (가장 빈번)
--   2) user_id로 해당 사용자 토큰 전체 무효화 (로그아웃)
-- token UNIQUE 제약이 이미 인덱스를 생성하므로 별도 인덱스 불필요
-- =============================================================================
CREATE TABLE refresh_token (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token       VARCHAR(512) NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- user_id로 해당 사용자 토큰 일괄 삭제/조회
CREATE INDEX idx_refresh_token_user_id    ON refresh_token (user_id);
-- 만료 토큰 배치 정리: expires_at 스캔 범위 최소화
CREATE INDEX idx_refresh_token_expires_at ON refresh_token (expires_at);

COMMENT ON TABLE  refresh_token            IS 'JWT Refresh Token 저장소';
COMMENT ON COLUMN refresh_token.token      IS '서명된 토큰 값';
COMMENT ON COLUMN refresh_token.expires_at IS '토큰 만료 시각';

-- =============================================================================
-- document: 업로드된 문서 메타데이터
-- 조회 패턴:
--   1) user_id로 사용자 문서 목록 (가장 빈번)
--   2) status로 처리 대기 문서 조회 (파이프라인 워커)
--   3) created_at DESC 정렬 (최신순 목록)
-- =============================================================================
CREATE TABLE document (
    id                  BIGSERIAL     PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES app_user(id),
    title               VARCHAR(500)  NOT NULL,
    original_file_name  VARCHAR(500)  NOT NULL,
    stored_file_path    VARCHAR(1000) NOT NULL,
    file_size           BIGINT        NOT NULL,
    mime_type           VARCHAR(100)  NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'UPLOADED',
    -- UPLOADED, PARSING, CHUNKING, EMBEDDING, READY, FAILED
    page_count          INT,
    text_length         INT,
    chunk_count         INT,
    error_message       TEXT,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_document_status CHECK (
        status IN ('UPLOADED', 'PARSING', 'CHUNKING', 'EMBEDDING', 'READY', 'FAILED')
    )
);

CREATE INDEX idx_document_user_id    ON document (user_id);
CREATE INDEX idx_document_status     ON document (status);
CREATE INDEX idx_document_created_at ON document (created_at DESC);

COMMENT ON TABLE  document                    IS '업로드된 문서 메타데이터';
COMMENT ON COLUMN document.stored_file_path   IS 'MinIO 오브젝트 경로';
COMMENT ON COLUMN document.status             IS 'UPLOADED | PARSING | CHUNKING | EMBEDDING | READY | FAILED';

-- =============================================================================
-- document_chunk: 문서 청크 + 임베딩 벡터
-- 조회 패턴:
--   1) document_id로 청크 목록 (항상)
--   2) embedding <=> queryVector 코사인 유사도 ANN 검색 (pgvector HNSW)
--   3) content_tsv @@ tsquery 전문검색 (GIN)
-- =============================================================================
CREATE TABLE document_chunk (
    id              BIGSERIAL    PRIMARY KEY,
    document_id     BIGINT       NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    chunk_index     INT          NOT NULL,
    content         TEXT         NOT NULL,
    token_count     INT          NOT NULL,
    embedding       vector(1024),
    page_start      INT,
    page_end        INT,
    content_tsv     tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', content), 'A')
    ) STORED,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- document_id + chunk_index 복합: 청크 순서 보장 조회
CREATE INDEX idx_chunk_document_id    ON document_chunk (document_id);
CREATE INDEX idx_chunk_chunk_index    ON document_chunk (document_id, chunk_index);

-- pgvector HNSW 인덱스 (코사인 유사도 ANN 검색)
-- m=16: 각 노드의 최대 연결 수 (정확도 vs 메모리 균형)
-- ef_construction=200: 인덱스 구축 시 후보 탐색 수 (높을수록 정확도 상승, 구축 느림)
CREATE INDEX idx_chunk_embedding ON document_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

-- 전문검색 GIN 인덱스: tsvector @@ tsquery 조회 가속
CREATE INDEX idx_chunk_content_tsv ON document_chunk USING GIN (content_tsv);

COMMENT ON TABLE  document_chunk             IS '문서 청크 텍스트 및 임베딩 벡터';
COMMENT ON COLUMN document_chunk.chunk_index IS '0부터 시작하는 청크 순서';
COMMENT ON COLUMN document_chunk.embedding   IS 'Voyage AI 임베딩 벡터 (1024차원)';
COMMENT ON COLUMN document_chunk.content_tsv IS 'content 컬럼의 tsvector (GENERATED ALWAYS STORED)';

-- =============================================================================
-- document_summary: AI 요약 결과
-- document_id UNIQUE: 문서당 요약 1건 보장
-- UNIQUE 제약이 이미 인덱스를 생성하므로 별도 인덱스 불필요
-- =============================================================================
CREATE TABLE document_summary (
    id              BIGSERIAL    PRIMARY KEY,
    document_id     BIGINT       NOT NULL REFERENCES document(id) ON DELETE CASCADE UNIQUE,
    content         TEXT         NOT NULL,
    model           VARCHAR(50)  NOT NULL,
    input_tokens    INT,
    output_tokens   INT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  document_summary              IS 'AI 요약 결과 (문서당 1건)';
COMMENT ON COLUMN document_summary.model        IS '요약 생성에 사용된 Claude 모델명';
COMMENT ON COLUMN document_summary.input_tokens IS 'Claude API 입력 토큰 수';
COMMENT ON COLUMN document_summary.output_tokens IS 'Claude API 출력 토큰 수';

-- =============================================================================
-- chat_session: 채팅 세션
-- 조회 패턴: user_id로 세션 목록 (항상)
-- =============================================================================
CREATE TABLE chat_session (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_user(id),
    title       VARCHAR(200),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_session_user_id ON chat_session (user_id);

COMMENT ON TABLE chat_session IS '채팅 세션 (멀티 문서 Q&A)';

-- =============================================================================
-- chat_session_document: 세션-문서 연결 (멀티 문서 Q&A)
-- (chat_session_id, document_id) UNIQUE: 중복 연결 방지
-- UNIQUE 제약이 이미 복합 인덱스를 생성하므로 별도 인덱스 불필요
-- =============================================================================
CREATE TABLE chat_session_document (
    id              BIGSERIAL PRIMARY KEY,
    chat_session_id BIGINT    NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    document_id     BIGINT    NOT NULL REFERENCES document(id),
    UNIQUE (chat_session_id, document_id)
);

COMMENT ON TABLE chat_session_document IS '채팅 세션과 문서 연결 (멀티 문서 Q&A)';

-- =============================================================================
-- chat_message: 채팅 메시지 이력
-- 조회 패턴: chat_session_id + created_at ASC 정렬 (대화 이력 페이징)
-- =============================================================================
CREATE TABLE chat_message (
    id               BIGSERIAL    PRIMARY KEY,
    chat_session_id  BIGINT       NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role             VARCHAR(20)  NOT NULL,
    content          TEXT         NOT NULL,
    source_chunk_ids BIGINT[],
    model            VARCHAR(50),
    input_tokens     INT,
    output_tokens    INT,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_chat_message_role CHECK (role IN ('USER', 'ASSISTANT'))
);

-- chat_session_id + created_at: 세션별 메시지 시간순 페이징
CREATE INDEX idx_chat_message_session ON chat_message (chat_session_id, created_at);

COMMENT ON TABLE  chat_message                  IS '채팅 메시지 이력';
COMMENT ON COLUMN chat_message.role             IS 'USER | ASSISTANT';
COMMENT ON COLUMN chat_message.source_chunk_ids IS 'ASSISTANT 응답이 참조한 document_chunk.id 배열';

-- =============================================================================
-- tag: 태그
-- (name, user_id) UNIQUE: 사용자별 태그명 중복 방지
-- UNIQUE 제약이 이미 복합 인덱스를 생성하므로 별도 인덱스 불필요
-- =============================================================================
CREATE TABLE tag (
    id          BIGSERIAL   PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    user_id     BIGINT      NOT NULL REFERENCES app_user(id),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (name, user_id)
);

CREATE INDEX idx_tag_user_id ON tag (user_id);

COMMENT ON TABLE tag IS '사용자 정의 태그';

-- =============================================================================
-- document_tag: 문서-태그 매핑
-- (document_id, tag_id) UNIQUE: 중복 태그 방지
-- UNIQUE 제약이 이미 복합 인덱스를 생성하므로 별도 인덱스 불필요
-- document_id, tag_id 각각 단방향 조회를 위한 인덱스 필요
-- =============================================================================
CREATE TABLE document_tag (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT    NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    tag_id      BIGINT    NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    UNIQUE (document_id, tag_id)
);

CREATE INDEX idx_document_tag_document ON document_tag (document_id);
CREATE INDEX idx_document_tag_tag      ON document_tag (tag_id);

COMMENT ON TABLE document_tag IS '문서-태그 매핑 (N:M)';
