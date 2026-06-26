package com.example.demo.service;

import com.example.demo.dto.AiRecommendationProfile;
import com.example.demo.dto.TasteAnalysisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class OpenAiRecommendationService {

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-5.4-mini}")
    private String openAiModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    public AiRecommendationProfile analyzeWeather(
            String weather,
            String title,
            String description,
            String genre
    ) {
        ensureOpenAiApiKey();

        try {
            Map<String, Object> requestBody = createRequestBody(
                    "weather_recommendation_profile",
                    createWeatherSchema(),
                    List.of(
                            Map.of(
                                    "role", "system",
                                    "content", """
                                            너는 음악 추천 서비스 MOOD WAVE의 날씨 기반 추천 기획자다.

                                            입력된 날씨, 플레이리스트 제목, 설명, 장르 힌트를 바탕으로
                                            Spotify 검색에 사용할 음악 추천 키워드를 만들어라.

                                            규칙:
                                            - 현재 날씨와 어울리는 감정/분위기를 우선한다.
                                            - 한국 사용자를 위한 서비스이므로 Korean, K-pop, Korean R&B, Korean indie, Korean ballad 등을 적절히 섞어라.
                                            - keywords는 8개 이상 10개 이하로 작성한다.
                                            - 너무 비슷한 키워드만 반복하지 말고 장르, 분위기, 언어를 다양화한다.
                                            - 응답은 반드시 지정된 JSON Schema 형식으로만 작성한다.
                                            """
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", """
                                            weather: %s
                                            playlistTitle: %s
                                            description: %s
                                            genreHint: %s
                                            """.formatted(weather, title, description, genre)
                            )
                    )
            );

            String outputText = callOpenAi(requestBody);

            return objectMapper.readValue(outputText, AiRecommendationProfile.class);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "OpenAI 날씨 추천 분석 실패: " + e.getMessage(),
                    e
            );
        }
    }

    public TasteAnalysisResponse analyzeTaste(List<Map<String, Object>> tracks) {
        ensureOpenAiApiKey();

        try {
            String trackSummary = tracks.stream()
                    .limit(30)
                    .map(track -> "- %s / %s".formatted(
                            Objects.toString(track.get("title"), ""),
                            Objects.toString(track.get("artist"), "")
                    ))
                    .collect(Collectors.joining("\n"));

            Map<String, Object> requestBody = createRequestBody(
                    "taste_analysis",
                    createTasteSchema(),
                    List.of(
                            Map.of(
                                    "role", "system",
                                    "content", """
                                            너는 음악 추천 서비스 MOOD WAVE의 음악 취향 분석기다.

                                            사용자가 최근 재생한 곡 목록을 분석해서 다음을 만들어라.

                                            1. genreStats
                                            - 사용자가 많이 듣는 장르 분포다.
                                            - label은 K-pop, Pop, R&B, Hip-hop, Indie, Rock, Electronic, Jazz, Classical, Ballad, Etc 중 자연스럽게 고른다.

                                            2. weatherStats
                                            - 사용자의 음악 취향을 날씨 분위기로 해석한 분포다.
                                            - label은 Sunny, Cloudy, Rainy, Snowy, Stormy, Foggy 중에서 고른다.

                                            3. keywords
                                            - 이 취향과 비슷한 곡을 Spotify에서 찾기 위한 검색어다.
                                            - keywords는 8개 이상 10개 이하로 작성한다.
                                            - 한국 음악 서비스이므로 Korean, K-pop, Korean R&B, Korean indie 등을 적절히 포함한다.

                                            판단 규칙:
                                            - 곡 제목만으로 단정하지 말고 아티스트와 전체 재생 패턴을 함께 본다.
                                            - 특정 아티스트 한 명만 반복 추천하지 않도록 장르와 분위기 키워드를 섞는다.
                                            - 응답은 반드시 지정된 JSON Schema 형식으로만 작성한다.
                                            """
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", "최근 재생 곡 목록:\n" + trackSummary
                            )
                    )
            );

            String outputText = callOpenAi(requestBody);

            return objectMapper.readValue(outputText, TasteAnalysisResponse.class);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "OpenAI 취향 분석 실패: " + e.getMessage(),
                    e
            );
        }
    }

    private void ensureOpenAiApiKey() {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OpenAI API Key가 설정되지 않았습니다."
            );
        }
    }

    private Map<String, Object> createRequestBody(
            String schemaName,
            Map<String, Object> schema,
            List<Map<String, String>> input
    ) {
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("strict", true);
        format.put("schema", schema);

        Map<String, Object> textFormat = new LinkedHashMap<>();
        textFormat.put("format", format);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openAiModel);
        body.put("input", input);
        body.put("text", textFormat);

        return body;
    }

    private String callOpenAi(Map<String, Object> requestBody) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openAiApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://api.openai.com/v1/responses",
                entity,
                String.class
        );

        return extractOutputText(response.getBody());
    }

    private Map<String, Object> createWeatherSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("moodLabel", Map.of(
                "type", "string",
                "description", "사용자에게 보여줄 한국어 날씨 추천 라벨"
        ));

        properties.put("reason", Map.of(
                "type", "string",
                "description", "왜 이 날씨에 이런 음악을 추천하는지 한 문장으로 설명"
        ));

        properties.put("keywords", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "minItems", 8,
                "maxItems", 10,
                "description", "Spotify 검색에 사용할 짧고 다양한 음악 키워드"
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of("moodLabel", "reason", "keywords"));

        return schema;
    }

    private Map<String, Object> createTasteSchema() {
        Map<String, Object> statItemSchema = new LinkedHashMap<>();
        statItemSchema.put("type", "object");
        statItemSchema.put("additionalProperties", false);
        statItemSchema.put("properties", Map.of(
                "label", Map.of("type", "string"),
                "count", Map.of("type", "integer")
        ));
        statItemSchema.put("required", List.of("label", "count"));

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("moodLabel", Map.of(
                "type", "string",
                "description", "사용자에게 보여줄 한국어 취향 라벨"
        ));

        properties.put("reason", Map.of(
                "type", "string",
                "description", "최근 재생 곡 기준 취향 분석 이유"
        ));

        properties.put("dominantGenre", Map.of(
                "type", "string",
                "description", "가장 강하게 드러나는 대표 장르"
        ));

        properties.put("keywords", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "minItems", 8,
                "maxItems", 10
        ));

        properties.put("genreStats", Map.of(
                "type", "array",
                "items", statItemSchema,
                "minItems", 3,
                "maxItems", 6
        ));

        properties.put("weatherStats", Map.of(
                "type", "array",
                "items", statItemSchema,
                "minItems", 3,
                "maxItems", 6
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of(
                "moodLabel",
                "reason",
                "dominantGenre",
                "keywords",
                "genreStats",
                "weatherStats"
        ));

        return schema;
    }

    private String extractOutputText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        if (root.hasNonNull("output_text")) {
            return root.path("output_text").asText();
        }

        JsonNode output = root.path("output");

        if (output.isArray()) {
            for (JsonNode outputItem : output) {
                JsonNode content = outputItem.path("content");

                if (content.isArray()) {
                    for (JsonNode contentItem : content) {
                        if (contentItem.hasNonNull("text")) {
                            return contentItem.path("text").asText();
                        }
                    }
                }
            }
        }

        throw new IllegalStateException("OpenAI 응답에서 output_text를 찾지 못했습니다.");
    }
}
