package com.example.demo.service;

import com.example.demo.dto.EmotionAnalyzeResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiEmotionService {

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.model:gpt-5.4-mini}")
    private String openAiModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    public EmotionAnalyzeResponse analyzeEmotion(String text) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "OpenAI API Key가 설정되지 않았습니다."
            );
        }

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
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "OpenAI 감정 분석 실패: " + e.getMessage(),
                    e
            );
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

                                사용자의 문장을 분석해서 반드시 아래 두 가지를 분리해라.

                                1. emotion
                                - 사용자가 현재 느끼는 감정이다.
                                - 슬픔, 분노, 불안, 피곤함, 행복, 설렘 등을 판단한다.

                                2. recommendationIntent
                                - 사용자가 원하는 음악 추천 방향이다.
                                - 현재 감정과 추천 방향은 다를 수 있다.

                                예시:
                                - "나 너무 슬퍼"
                                  => emotion: SAD
                                  => recommendationIntent: MATCH_MOOD

                                - "나 너무 슬픈데 신나는 노래 추천해줘"
                                  => emotion: SAD
                                  => recommendationIntent: UPLIFTING

                                - "나 너무 슬픈데 위로해줘"
                                  => emotion: SAD
                                  => recommendationIntent: COMFORT

                                - "나 너무 분노 폭발이야 이 분노를 더 끌어올릴 수 있는 노래 추천해줘"
                                  => emotion: ANGRY
                                  => recommendationIntent: ENERGETIC

                                - "나 너무 분노가 치밀어오르는데 날 릴렉스 시켜줄 노래 추천해줘"
                                  => emotion: ANGRY
                                  => recommendationIntent: CALM_DOWN

                                - "너무 피곤한데 잠 깨는 노래 추천해줘"
                                  => emotion: TIRED
                                  => recommendationIntent: ENERGETIC

                                - "너무 피곤해서 쉬고 싶어"
                                  => emotion: TIRED
                                  => recommendationIntent: CALM_DOWN

                                - "불안한데 마음 안정되는 노래 추천해줘"
                                  => emotion: ANXIOUS
                                  => recommendationIntent: CALM_DOWN

                                - "불안한데 집중할 수 있는 노래 추천해줘"
                                  => emotion: ANXIOUS
                                  => recommendationIntent: FOCUS

                                판단 규칙:
                                - 감정 단어만 보고 추천 방향을 고정하지 마라.
                                - "신나는", "기분전환", "텐션", "밝은", "업되는" 표현이 있으면 UPLIFTING으로 판단해라.
                                - "더 끌어올릴", "폭발", "강렬한", "빡센", "에너지" 표현이 있으면 ENERGETIC으로 판단해라.
                                - "위로", "토닥", "괜찮아지고 싶어", "힘들어", "따뜻한" 표현이 있으면 COMFORT로 판단해라.
                                - "진정", "릴렉스", "차분", "잔잔", "가라앉", "편안" 표현이 있으면 CALM_DOWN으로 판단해라.
                                - "집중", "공부", "작업", "코딩" 표현이 있으면 FOCUS로 판단해라.
                                - "사랑", "설렘", "연애", "고백", "데이트" 표현이 있으면 ROMANTIC으로 판단해라.
                                - 특별한 추천 방향이 없으면 현재 감정과 어울리는 MATCH_MOOD로 판단해라.

                                keywords 규칙:
                                - Spotify 검색에 사용할 짧은 검색어를 만들어라.
                                - keywords는 8개 이상 10개 이하로 만들어라.
                                - 전부 비슷한 단어로 만들지 말고 장르, 분위기, 언어를 섞어라.
                                - 한국 음악 서비스이므로 Korean, K-pop, Korean ballad, Korean indie, Korean R&B 등을 적절히 포함해라.
                                - recommendationIntent에 맞는 키워드를 우선으로 만들어라.
                                - 응답은 반드시 지정된 JSON Schema 형식으로만 작성해라.
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

        properties.put("recommendationIntent", Map.of(
                "type", "string",
                "enum", List.of(
                        "MATCH_MOOD",
                        "UPLIFTING",
                        "COMFORT",
                        "CALM_DOWN",
                        "ENERGETIC",
                        "FOCUS",
                        "ROMANTIC",
                        "NEUTRAL"
                ),
                "description", "사용자가 원하는 음악 추천 방향"
        ));

        properties.put("moodLabel", Map.of(
                "type", "string",
                "description", "사용자에게 보여줄 한국어 감정/추천 방향 라벨"
        ));

        properties.put("reason", Map.of(
                "type", "string",
                "description", "왜 이 감정과 추천 방향으로 판단했는지 한 문장으로 설명"
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
        schema.put("required", List.of(
                "emotion",
                "recommendationIntent",
                "moodLabel",
                "reason",
                "keywords"
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