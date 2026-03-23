package com.docmind.ai.service;

import com.docmind.global.exception.BusinessException;
import com.docmind.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Voyage AI 임베딩 서비스.
 *
 * POST https://api.voyageai.com/v1/embeddings
 * model: voyage-3 (1024 차원)
 * 128건씩 배치 처리.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int BATCH_SIZE = 128;

    private final RestClient voyageClient;
    private final String model;

    public EmbeddingService(@Value("${voyage.api-key}") String apiKey,
                            @Value("${voyage.model:voyage-3}") String model) {
        this.model = model;
        this.voyageClient = RestClient.builder()
                .baseUrl("https://api.voyageai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 단건 임베딩.
     */
    public float[] embed(String text) {
        return batchEmbed(List.of(text)).getFirst();
    }

    /**
     * 배치 임베딩 (128건씩 분할 호출).
     */
    public List<float[]> batchEmbed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + BATCH_SIZE, texts.size()));
            List<float[]> batchResult = callVoyageApi(batch);
            allEmbeddings.addAll(batchResult);
            log.debug("Embedded batch {}/{} ({} texts)",
                    (i / BATCH_SIZE) + 1,
                    (int) Math.ceil((double) texts.size() / BATCH_SIZE),
                    batch.size());
        }

        return allEmbeddings;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private List<float[]> callVoyageApi(List<String> texts) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "input", texts,
                    "model", model,
                    "input_type", "document"
            );

            VoyageEmbeddingResponse response = voyageClient.post()
                    .uri("/embeddings")
                    .body(requestBody)
                    .retrieve()
                    .body(VoyageEmbeddingResponse.class);

            if (response == null || response.data() == null) {
                throw new BusinessException(ErrorCode.EMBEDDING_ERROR, "Empty response from Voyage API");
            }

            return response.data().stream()
                    .map(VoyageEmbeddingData::embedding)
                    .toList();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Voyage API call failed", e);
            throw new BusinessException(ErrorCode.EMBEDDING_ERROR,
                    "Embedding generation failed: " + e.getMessage());
        }
    }

    /**
     * 질문 임베딩 (input_type: "query"로 호출).
     */
    public float[] embedQuery(String text) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "input", List.of(text),
                    "model", model,
                    "input_type", "query"
            );

            VoyageEmbeddingResponse response = voyageClient.post()
                    .uri("/embeddings")
                    .body(requestBody)
                    .retrieve()
                    .body(VoyageEmbeddingResponse.class);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new BusinessException(ErrorCode.EMBEDDING_ERROR, "Empty response from Voyage API");
            }

            return response.data().getFirst().embedding();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Voyage API query embedding failed", e);
            throw new BusinessException(ErrorCode.EMBEDDING_ERROR,
                    "Query embedding failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Voyage API response DTOs
    // -------------------------------------------------------------------------

    record VoyageEmbeddingResponse(
            @JsonProperty("data") List<VoyageEmbeddingData> data,
            @JsonProperty("model") String model,
            @JsonProperty("usage") VoyageUsage usage
    ) {}

    record VoyageEmbeddingData(
            @JsonProperty("embedding") float[] embedding,
            @JsonProperty("index") int index
    ) {}

    record VoyageUsage(
            @JsonProperty("total_tokens") int totalTokens
    ) {}
}
