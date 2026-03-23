# DocMind

AI 기반 문서 요약 및 검색 시스템. Claude API와 RAG(Retrieval-Augmented Generation) 파이프라인을 활용하여 문서를 자동으로 파싱, 청킹, 임베딩하고, 자연어 질의에 대해 문서 컨텍스트 기반의 정확한 답변을 생성한다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 25 |
| Framework | Spring Boot 3.5 |
| Database | PostgreSQL 17 + pgvector |
| Cache | Redis 7.4 |
| Object Storage | MinIO (S3-compatible) |
| Document Parsing | Apache Tika |
| AI LLM | Claude API (Anthropic) |
| Embedding | Voyage AI (voyage-3) |
| Auth | JWT (jjwt) |
| API Docs | SpringDoc OpenAPI (Swagger) |
| Monitoring | Spring Actuator + Prometheus |
| Container | Docker Compose |

---

## 아키텍처

```
                         +------------------+
                         |   Client (Web)   |
                         +--------+---------+
                                  |
                          REST API / SSE
                                  |
                   +--------------v--------------+
                   |       Spring Boot App       |
                   |  (JWT Auth + REST + SSE)    |
                   +-+--------+--------+--------++
                     |        |        |        |
              +------+  +----+----+  +-+------+ +--------+
              | Auth |  |Document |  |  AI    | | Search |
              +------+  +-+-------+  +-+------+ +---+----+
                          |            |             |
                    +-----v-----+  +--v---+   +-----v------+
                    |   MinIO   |  |Claude|   |  pgvector   |
                    |(파일저장) |  | API  |   |(벡터검색)   |
                    +-----------+  +------+   +-----+------+
                                                    |
                    +----------+    +--------+ +----v-----+
                    |  Redis   |    | Tika   | |PostgreSQL|
                    | (캐시)   |    |(파싱)  | | (전문검색)|
                    +----------+    +--------+ +----------+

  [RAG 파이프라인]
  Upload -> Tika Parse -> Chunking -> Voyage Embed -> pgvector 저장
  Query  -> Voyage Embed -> 벡터 검색 -> 컨텍스트 조립 -> Claude SSE 스트리밍
```

---

## 주요 기능

- **문서 업로드 및 파싱**: PDF, DOCX, TXT 파일 업로드 후 Apache Tika로 텍스트 추출. MIME 타입은 파일 시그니처(매직 바이트)로 검증.
- **텍스트 청킹 + 임베딩**: 추출된 텍스트를 512토큰 단위로 분할, Voyage AI로 벡터 임베딩 생성, pgvector에 저장.
- **AI 문서 요약**: Claude API를 통해 문서별 요약 생성 (SSE 스트리밍). 결과는 DB에 캐싱.
- **RAG Q&A 채팅**: 벡터 검색으로 관련 청크를 찾고, 컨텍스트를 조립하여 Claude로 SSE 스트리밍 답변 생성. 대화 이력 유지.
- **하이브리드 검색**: PostgreSQL 전문검색(tsvector) + 시맨틱 검색(pgvector 코사인 유사도) + RRF(Reciprocal Rank Fusion) 알고리즘으로 결합.
- **태그 관리**: 사용자별 태그 생성, 문서-태그 연결/해제, 태그별 문서 필터링.
- **JWT 인증**: Access Token + Refresh Token 기반 인증.
- **파일 저장**: MinIO (S3 호환) 오브젝트 스토리지.
- **캐싱**: Redis 기반 캐싱.

---

## API 엔드포인트

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/signup` | 회원가입 |
| POST | `/api/v1/auth/login` | 로그인 |
| POST | `/api/v1/auth/refresh` | 토큰 갱신 |
| POST | `/api/v1/auth/logout` | 로그아웃 |
| GET | `/api/v1/users/me` | 내 정보 조회 |

### Documents
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/documents` | 문서 업로드 (multipart) |
| GET | `/api/v1/documents` | 문서 목록 (페이징) |
| GET | `/api/v1/documents/{id}` | 문서 상세 |
| DELETE | `/api/v1/documents/{id}` | 문서 삭제 |
| GET | `/api/v1/documents/{id}/status` | 처리 상태 조회 |
| GET | `/api/v1/documents/{id}/download` | 파일 다운로드 |

### AI Summary
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/documents/{id}/summary` | 요약 생성 (SSE) |
| GET | `/api/v1/documents/{id}/summary` | 요약 조회 |
| DELETE | `/api/v1/documents/{id}/summary` | 요약 삭제 |

### Chat (RAG Q&A)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/chat/sessions` | 채팅 세션 생성 |
| GET | `/api/v1/chat/sessions` | 세션 목록 |
| GET | `/api/v1/chat/sessions/{id}` | 세션 상세 (메시지 포함) |
| DELETE | `/api/v1/chat/sessions/{id}` | 세션 삭제 |
| POST | `/api/v1/chat/sessions/{id}/messages` | 질문 전송 (SSE) |

### Search
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/search?q={query}&type={type}` | 통합 검색 (full_text / semantic / hybrid) |

### Tags
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/tags` | 태그 목록 |
| POST | `/api/v1/tags` | 태그 생성 |
| POST | `/api/v1/documents/{id}/tags` | 문서에 태그 추가 |
| DELETE | `/api/v1/documents/{id}/tags/{tagId}` | 문서에서 태그 제거 |
| GET | `/api/v1/tags/{tagId}/documents` | 태그별 문서 목록 |

---

## 실행 방법

### 사전 요구사항

- Docker & Docker Compose
- Claude API Key (Anthropic)
- Voyage API Key

### Docker Compose로 실행

```bash
# 1. 프로젝트 빌드
./gradlew bootJar

# 2. 환경 변수 설정
export CLAUDE_API_KEY=your-claude-api-key
export VOYAGE_API_KEY=your-voyage-api-key

# 3. Docker Compose 실행
cd docker
docker compose up -d

# 4. 상태 확인
docker compose ps
```

서비스 접속:
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- MinIO Console: http://localhost:9001 (minioadmin / minioadmin)
- Actuator: http://localhost:8080/actuator/health

### 로컬 개발 (인프라만 Docker)

```bash
# 인프라 서비스만 실행 (postgres, redis, minio)
cd docker
docker compose up -d postgres redis minio

# Spring Boot 앱 실행
cd ..
CLAUDE_API_KEY=your-key VOYAGE_API_KEY=your-key ./gradlew bootRun
```

---

## 스크린샷

> TODO: 스크린샷 추가 예정

---
