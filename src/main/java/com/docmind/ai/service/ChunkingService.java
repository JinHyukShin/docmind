package com.docmind.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 텍스트 청킹 서비스.
 *
 * 토큰 기반 슬라이딩 윈도우 청킹 전략:
 *   - 문장 경계(. ! ? \n\n)로 텍스트를 먼저 분할
 *   - 문장들을 chunkSize 토큰 이내로 그룹핑
 *   - overlap 토큰만큼 이전 청크 끝부분을 다음 청크 시작에 포함
 *
 * 간이 토크나이저: 공백 기반 (단어 수 * 0.75 ≈ 토큰 수 역산)
 */
@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    /**
     * 문장 분리 패턴: 마침표/느낌표/물음표 뒤 공백, 또는 빈 줄(\n\n).
     * 문장 끝 구분자를 유지하면서 분리한다.
     */
    private static final Pattern SENTENCE_BOUNDARY =
            Pattern.compile("(?<=[.!?])\\s+|(?<=\\n\\n)");

    /**
     * 간이 토큰 수 추정.
     * 영어 기준 평균 1 단어 ≈ 1.33 토큰 → 단어 수 / 0.75 ≈ 토큰 수.
     * 한국어는 어절 기반이므로 비슷한 비율 적용.
     */
    private static final double WORDS_PER_TOKEN = 0.75;

    public record TextChunk(String content, int tokenCount, Integer pageStart, Integer pageEnd) {}

    /**
     * 텍스트를 청크로 분할한다.
     *
     * @param text      분할할 텍스트
     * @param chunkSize 청크 당 최대 토큰 수 (기본 512)
     * @param overlap   오버랩 토큰 수 (기본 50)
     * @return 분할된 TextChunk 리스트
     */
    public List<TextChunk> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> sentences = splitIntoSentences(text);
        List<TextChunk> chunks = new ArrayList<>();

        StringBuilder currentChunk = new StringBuilder();
        int currentTokens = 0;

        for (String sentence : sentences) {
            int sentenceTokens = countTokens(sentence);

            // 단일 문장이 chunkSize를 초과하면 강제 분할
            if (sentenceTokens > chunkSize) {
                // 현재 청크가 있으면 먼저 저장
                if (currentTokens > 0) {
                    chunks.add(createChunk(currentChunk.toString().trim(), currentTokens));
                    currentChunk = new StringBuilder();
                    currentTokens = 0;
                }
                // 긴 문장을 단어 단위로 강제 분할
                chunks.addAll(forceSplitLongSentence(sentence, chunkSize));
                continue;
            }

            // 현재 청크에 문장을 추가하면 chunkSize 초과 시 → 현재 청크 저장 후 오버랩 적용
            if (currentTokens + sentenceTokens > chunkSize && currentTokens > 0) {
                chunks.add(createChunk(currentChunk.toString().trim(), currentTokens));

                // 오버랩 적용: 마지막 overlap 토큰만큼 되감기
                String overlapText = getLastTokens(currentChunk.toString(), overlap);
                currentChunk = new StringBuilder(overlapText);
                currentTokens = countTokens(overlapText);
            }

            currentChunk.append(sentence).append(" ");
            currentTokens += sentenceTokens;
        }

        // 마지막 청크 저장
        if (currentTokens > 0) {
            chunks.add(createChunk(currentChunk.toString().trim(), currentTokens));
        }

        log.debug("Chunked text into {} chunks (chunkSize={}, overlap={})", chunks.size(), chunkSize, overlap);
        return chunks;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private List<String> splitIntoSentences(String text) {
        String[] parts = SENTENCE_BOUNDARY.split(text);
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    /**
     * 간이 토큰 수 추정: 공백 기반 단어 수 / WORDS_PER_TOKEN.
     */
    int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        return Math.max(1, (int) Math.ceil(words.length / WORDS_PER_TOKEN));
    }

    /**
     * 텍스트 끝에서 약 tokenCount 토큰에 해당하는 부분을 추출한다.
     */
    private String getLastTokens(String text, int tokenCount) {
        String[] words = text.trim().split("\\s+");
        int wordCount = (int) Math.ceil(tokenCount * WORDS_PER_TOKEN);
        if (wordCount >= words.length) {
            return text.trim();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = words.length - wordCount; i < words.length; i++) {
            if (!sb.isEmpty()) {
                sb.append(" ");
            }
            sb.append(words[i]);
        }
        return sb.toString();
    }

    private TextChunk createChunk(String content, int tokenCount) {
        return new TextChunk(content, tokenCount, null, null);
    }

    /**
     * chunkSize를 초과하는 긴 문장을 단어 단위로 강제 분할한다.
     */
    private List<TextChunk> forceSplitLongSentence(String sentence, int chunkSize) {
        String[] words = sentence.trim().split("\\s+");
        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int currentTokens = 0;

        for (String word : words) {
            int wordTokens = Math.max(1, (int) Math.ceil(1 / WORDS_PER_TOKEN));
            if (currentTokens + wordTokens > chunkSize && currentTokens > 0) {
                chunks.add(createChunk(sb.toString().trim(), currentTokens));
                sb = new StringBuilder();
                currentTokens = 0;
            }
            sb.append(word).append(" ");
            currentTokens += wordTokens;
        }
        if (currentTokens > 0) {
            chunks.add(createChunk(sb.toString().trim(), currentTokens));
        }
        return chunks;
    }
}
