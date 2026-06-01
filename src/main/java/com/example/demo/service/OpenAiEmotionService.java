package com.example.demo.service;

import com.example.demo.dto.EmotionAnalyzeResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiEmotionService {

	@Value("${OPENAI_API_KEY}")
	private String openAiApiKey;

	@Value("${OPENAI_MODEL:gpt-5.4-mini}")
	private String openAiModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    public EmotionAnalyzeResponse analyzeEmotion(String text) {
        try {
            Map<String, Object> requestBody = createRequestBody(text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/responses",
                    entity,
                    String.class
            );

            String outputText = extractOutputText(response.getBody());

            return objectMapper.readValue(outputText, EmotionAnalyzeResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI 감정 분석 실패: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> createRequestBody(String text) {
        Map<String, Object> schema = createSchema();

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "emotion_analysis");
        format.put("strict", true);
        format.put("schema", schema);

        Map<String, Object> textFormat = new LinkedHashMap<>();
        textFormat.put("format", format);

        List<Map<String, String>> input = List.of(
                Map.of(
                        "role", "system",
                        "content", """
                                너는 음악 추천 서비스 MOOD WAVE의 감정 분석기다.
                                사용자의 문장을 분석해서 현재 감정을 하나로 분류해라.
                                음악 추천에 사용할 Spotify 검색 키워드도 함께 만들어라.
                                응답은 반드시 지정된 JSON Schema 형식으로만 작성해라.
                                """
                ),
                Map.of(
                        "role", "user",
                        "content", text
                )
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openAiModel);
        body.put("input", input);
        body.put("text", textFormat);

        return body;
    }

    private Map<String, Object> createSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("emotion", Map.of(
                "type", "string",
                "enum", List.of(
                        "HAPPY",
                        "SAD",
                        "ANGRY",
                        "CALM",
                        "TIRED",
                        "ANXIOUS",
                        "ROMANTIC",
                        "NEUTRAL"
                )
        ));

        properties.put("moodLabel", Map.of(
                "type", "string",
                "description", "사용자에게 보여줄 한국어 감정 라벨"
        ));

        properties.put("reason", Map.of(
                "type", "string",
                "description", "왜 이 감정으로 판단했는지 한 문장으로 설명"
        ));

        properties.put("keywords", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "minItems", 3,
                "maxItems", 5,
                "description", "Spotify 검색에 사용할 짧은 음악 키워드"
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of("emotion", "moodLabel", "reason", "keywords"));

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