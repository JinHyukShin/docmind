package com.docmind.auth.controller;

import com.docmind.auth.dto.LoginRequest;
import com.docmind.auth.dto.RefreshRequest;
import com.docmind.auth.dto.SignupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 통합 테스트.
 *
 * Testcontainers로 PostgreSQL을 띄우고 실제 Spring 컨텍스트를 로드한다.
 *
 * MinIO와 Redis는 테스트 프로파일에서 Mock Bean 또는 연결이 불필요한 설정으로 대체한다.
 * (테스트 application-test.yml 에서 minio/redis 설정을 테스트 컨테이너 URL로 재정의하거나,
 *  @MockBean으로 MinioClient, StringRedisTemplate을 대체해야 한다.)
 *
 * 테스트 실행 순서 고정: @TestMethodOrder + @Order로 refresh token 재사용.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("AuthController 통합 테스트")
class AuthControllerTest {

    // -------------------------------------------------------------------------
    // Testcontainers: PostgreSQL
    // -------------------------------------------------------------------------

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("docmind_test")
            .withUsername("docmind")
            .withPassword("docmind");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // JWT 설정 (테스트 전용 시크릿)
        registry.add("jwt.secret",
                () -> "test-secret-for-integration-test-must-be-at-least-32-chars");
        registry.add("jwt.access-token-expiry-ms", () -> "1800000");
        registry.add("jwt.refresh-token-expiry-ms", () -> "604800000");

        // MinIO: 테스트에서 비활성화 (FileStorageService @MockBean 대체)
        registry.add("minio.endpoint", () -> "http://localhost:9999");
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
        registry.add("minio.bucket-name", () -> "test");

        // Redis: 테스트에서 임베딩/캐시를 사용하지 않으므로 localhost 기본값 유지
        // 실제 환경에서는 Testcontainers GenericContainer("redis:7-alpine")로 대체 권장
    }

    // -------------------------------------------------------------------------
    // 공유 상태: refresh token 재사용 (순서 의존 최소화)
    // -------------------------------------------------------------------------

    private static String capturedRefreshToken;

    // -------------------------------------------------------------------------
    // 상수
    // -------------------------------------------------------------------------

    private static final String SIGNUP_URL   = "/api/v1/auth/signup";
    private static final String LOGIN_URL    = "/api/v1/auth/login";
    private static final String REFRESH_URL  = "/api/v1/auth/refresh";
    private static final String LOGOUT_URL   = "/api/v1/auth/logout";

    private static final String TEST_EMAIL    = "test-auth@example.com";
    private static final String TEST_PASSWORD = "Password123!";
    private static final String TEST_NAME     = "Test User";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // =========================================================================
    // 테스트 케이스
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("회원가입 성공 시 201 Created와 토큰을 반환한다")
    void signup_success_returns201AndTokens() throws Exception {
        // given
        SignupRequest request = new SignupRequest(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);

        // when / then
        MvcResult result = mockMvc.perform(
                        post(SIGNUP_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").isNumber())
                .andReturn();

        // refresh token 캡처 (이후 테스트에서 재사용)
        String body = result.getResponse().getContentAsString();
        capturedRefreshToken = objectMapper.readTree(body)
                .path("data").path("refreshToken").asText();
        assertThat(capturedRefreshToken).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("동일 이메일로 회원가입 시 409 Conflict를 반환한다")
    void signup_duplicateEmail_returns409() throws Exception {
        // given: 이미 Order(1)에서 등록한 이메일
        SignupRequest request = new SignupRequest(TEST_EMAIL, TEST_PASSWORD, "Duplicate User");

        // when / then
        mockMvc.perform(
                        post(SIGNUP_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE"));
    }

    @Test
    @Order(3)
    @DisplayName("로그인 성공 시 200 OK와 토큰을 반환한다")
    void login_success_returns200AndTokens() throws Exception {
        // given
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

        // when / then
        mockMvc.perform(
                        post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @Order(4)
    @DisplayName("잘못된 비밀번호로 로그인 시 401 Unauthorized를 반환한다")
    void login_wrongPassword_returns401() throws Exception {
        // given
        LoginRequest request = new LoginRequest(TEST_EMAIL, "WrongPassword!");

        // when / then
        mockMvc.perform(
                        post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @Order(5)
    @DisplayName("존재하지 않는 이메일로 로그인 시 401 Unauthorized를 반환한다")
    void login_unknownEmail_returns401() throws Exception {
        // given
        LoginRequest request = new LoginRequest("nobody@example.com", TEST_PASSWORD);

        // when / then
        mockMvc.perform(
                        post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(6)
    @DisplayName("유효한 refresh token으로 토큰 갱신 시 200 OK와 새 토큰을 반환한다")
    void refresh_validToken_returns200AndNewTokens() throws Exception {
        // given: Order(1) 에서 캡처한 refresh token
        assertThat(capturedRefreshToken)
                .as("refresh token must be captured in signup test (Order 1)")
                .isNotBlank();
        RefreshRequest request = new RefreshRequest(capturedRefreshToken);

        // when / then
        MvcResult result = mockMvc.perform(
                        post(REFRESH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        // 갱신 후 새 refresh token 저장 (토큰 rotation 검증)
        String body = result.getResponse().getContentAsString();
        String newRefreshToken = objectMapper.readTree(body)
                .path("data").path("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(capturedRefreshToken);

        capturedRefreshToken = newRefreshToken; // Order(7) 로그아웃에서 사용
    }

    @Test
    @Order(7)
    @DisplayName("로그아웃 시 204 No Content를 반환하고 이후 동일 refresh token 사용 불가")
    void logout_success_returns204AndTokenInvalidated() throws Exception {
        // given
        assertThat(capturedRefreshToken)
                .as("refresh token must be set before logout test")
                .isNotBlank();
        RefreshRequest logoutRequest = new RefreshRequest(capturedRefreshToken);

        // when: 로그아웃
        mockMvc.perform(
                        post(LOGOUT_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(logoutRequest)))
                .andDo(print())
                .andExpect(status().isNoContent());

        // then: 로그아웃된 refresh token으로 갱신 시도 → 401
        mockMvc.perform(
                        post(REFRESH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(logoutRequest)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @Order(8)
    @DisplayName("유효하지 않은 refresh token으로 갱신 시 401을 반환한다")
    void refresh_invalidToken_returns401() throws Exception {
        // given
        RefreshRequest request = new RefreshRequest("invalid.refresh.token.value");

        // when / then
        mockMvc.perform(
                        post(REFRESH_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(9)
    @DisplayName("이메일 형식이 잘못된 회원가입 요청은 400을 반환한다")
    void signup_invalidEmail_returns400() throws Exception {
        // given
        SignupRequest request = new SignupRequest("not-an-email", TEST_PASSWORD, "User");

        // when / then
        mockMvc.perform(
                        post(SIGNUP_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @Order(10)
    @DisplayName("빈 비밀번호로 로그인 시 400을 반환한다")
    void login_blankPassword_returns400() throws Exception {
        // given
        LoginRequest request = new LoginRequest(TEST_EMAIL, "");

        // when / then
        mockMvc.perform(
                        post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
