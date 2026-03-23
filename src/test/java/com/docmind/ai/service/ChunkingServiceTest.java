package com.docmind.ai.service;

import com.docmind.ai.service.ChunkingService.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChunkingService 단위 테스트.
 *
 * 설계서 기준:
 *   - 청크 크기: 512 토큰
 *   - 오버랩: 50 토큰
 *   - 문장 경계 분할 우선
 *   - 간이 토크나이저: 공백 기반 (단어 수 / 0.75 ≈ 토큰 수)
 */
@DisplayName("ChunkingService 단위 테스트")
class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        chunkingService = new ChunkingService();
    }

    /**
     * 주어진 단어 수에 해당하는 텍스트를 생성한다.
     */
    private String buildText(int wordCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordCount; i++) {
            if (i > 0) sb.append(' ');
            sb.append("word").append(i);
        }
        return sb.toString();
    }

    /**
     * 문장 경계가 있는 텍스트를 생성한다.
     */
    private String buildSentenceText(int sentenceCount, int wordsPerSentence) {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < sentenceCount; s++) {
            if (s > 0) sb.append(' ');
            for (int w = 0; w < wordsPerSentence; w++) {
                if (w > 0) sb.append(' ');
                sb.append("word").append(s * wordsPerSentence + w);
            }
            sb.append('.');
        }
        return sb.toString();
    }

    // =========================================================================
    // 테스트 케이스
    // =========================================================================

    @Test
    @DisplayName("짧은 텍스트는 단일 청크로 반환된다")
    void chunk_shortText_returnsSingleChunk() {
        String text = buildText(100); // 100 단어 → 약 134 토큰

        List<TextChunk> chunks = chunkingService.chunk(text, 512, 50);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content()).isNotBlank();
    }

    @Test
    @DisplayName("빈 텍스트는 빈 리스트를 반환한다")
    void chunk_emptyText_returnsEmptyList() {
        List<TextChunk> chunks = chunkingService.chunk("", 512, 50);
        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("null 텍스트는 빈 리스트를 반환한다")
    void chunk_nullText_returnsEmptyList() {
        List<TextChunk> chunks = chunkingService.chunk(null, 512, 50);
        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("공백만 있는 텍스트는 빈 리스트를 반환한다")
    void chunk_blankText_returnsEmptyList() {
        List<TextChunk> chunks = chunkingService.chunk("   \t\n  ", 512, 50);
        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("긴 텍스트는 여러 청크로 분할된다")
    void chunk_longText_returnsMultipleChunks() {
        // 1000 단어 → 약 1333 토큰 → 512 토큰 청크로 분할 시 최소 2개
        String text = buildText(1000);

        List<TextChunk> chunks = chunkingService.chunk(text, 512, 50);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("각 청크의 tokenCount가 양수이다")
    void chunk_allChunks_havePositiveTokenCount() {
        String text = buildText(800);

        List<TextChunk> chunks = chunkingService.chunk(text, 512, 50);

        for (TextChunk chunk : chunks) {
            assertThat(chunk.tokenCount()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("문장 경계 텍스트에서 청킹이 정상 동작한다")
    void chunk_sentenceText_worksCorrectly() {
        // 20문장 x 30단어 = 600 단어 → 약 800 토큰
        String text = buildSentenceText(20, 30);

        List<TextChunk> chunks = chunkingService.chunk(text, 512, 50);

        assertThat(chunks).isNotEmpty();
        for (TextChunk chunk : chunks) {
            assertThat(chunk.content()).isNotBlank();
            assertThat(chunk.tokenCount()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("countTokens: 공백 기반 토큰 수 추정이 올바르게 동작한다")
    void countTokens_estimatesCorrectly() {
        // 10 단어 → 10 / 0.75 = 14 토큰 (올림)
        int tokens = chunkingService.countTokens("a b c d e f g h i j");
        assertThat(tokens).isEqualTo(14); // ceil(10 / 0.75)
    }

    @Test
    @DisplayName("countTokens: 빈 텍스트는 0을 반환한다")
    void countTokens_emptyText_returnsZero() {
        assertThat(chunkingService.countTokens("")).isEqualTo(0);
        assertThat(chunkingService.countTokens(null)).isEqualTo(0);
        assertThat(chunkingService.countTokens("   ")).isEqualTo(0);
    }
}
