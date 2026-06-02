package com.example.demo.service;

import com.example.demo.dto.EmotionAnalyzeResponse;
import com.example.demo.dto.EmotionRecommendResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class EmotionRecommendService {

    private static final int RESULT_LIMIT = 10;
    private static final int SEARCH_LIMIT_PER_KEYWORD = 6;
    private static final int MAX_KEYWORDS_TO_SEARCH = 14;
    private static final int MAX_ARTIST_DUPLICATE = 2;

    private final OpenAiEmotionService openAiEmotionService;
    private final SpotifyService spotifyService;

    public EmotionRecommendService(
            OpenAiEmotionService openAiEmotionService,
            SpotifyService spotifyService
    ) {
        this.openAiEmotionService = openAiEmotionService;
        this.spotifyService = spotifyService;
    }

    public EmotionRecommendResponse recommend(String text) {
        if (text == null || text.trim().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "감정 문장을 입력해주세요."
            );
        }

        String userText = text.trim();

        EmotionAnalyzeResponse analysis = openAiEmotionService.analyzeEmotion(userText);

        String emotion = normalizeValue(analysis.emotion(), "NEUTRAL");
        String intent = normalizeValue(analysis.recommendationIntent(), "NEUTRAL");

        intent = overrideIntentByUserText(userText, intent);

        LinkedHashSet<String> keywordSet = new LinkedHashSet<>();

        keywordSet.addAll(getDefaultKeywordsByIntent(intent));

        if (analysis.keywords() != null) {
            for (String keyword : analysis.keywords()) {
                addKeyword(keywordSet, keyword);
            }
        }

        keywordSet.addAll(getDefaultKeywordsByEmotion(emotion));

        List<String> keywords = keywordSet.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .limit(MAX_KEYWORDS_TO_SEARCH)
                .toList();

        List<Map<String, Object>> tracks = collectDiverseTracks(keywords, RESULT_LIMIT);

        if (tracks.isEmpty()) {
            tracks = spotifyService.getPopularTracks(RESULT_LIMIT);
        }

        String moodLabel = createMoodLabel(analysis.moodLabel(), emotion, intent);
        String reason = createReason(analysis.reason(), emotion, intent);

        return new EmotionRecommendResponse(
                emotion,
                intent,
                moodLabel,
                reason,
                keywords,
                tracks
        );
    }

    private List<Map<String, Object>> collectDiverseTracks(List<String> keywords, int resultLimit) {
        Map<String, Map<String, Object>> candidateMap = new LinkedHashMap<>();

        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }

            List<Map<String, Object>> searchedTracks =
                    spotifyService.searchTracksForMood(keyword, SEARCH_LIMIT_PER_KEYWORD);

            for (Map<String, Object> track : searchedTracks) {
                String key = createTrackUniqueKey(track);

                if (key.isBlank()) {
                    continue;
                }

                candidateMap.putIfAbsent(key, track);
            }

            if (candidateMap.size() >= 50) {
                break;
            }
        }

        if (candidateMap.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> candidates = new ArrayList<>(candidateMap.values());

        Collections.shuffle(candidates);

        return diversifyTracks(candidates, resultLimit);
    }

    private List<Map<String, Object>> diversifyTracks(
            List<Map<String, Object>> candidates,
            int resultLimit
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Integer> artistCountMap = new HashMap<>();
        Set<String> usedTrackKeys = new HashSet<>();

        for (Map<String, Object> track : candidates) {
            if (result.size() >= resultLimit) {
                break;
            }

            String trackKey = createTrackUniqueKey(track);
            String artistKey = normalizeText(getStringValue(track, "artist"));

            if (trackKey.isBlank() || usedTrackKeys.contains(trackKey)) {
                continue;
            }

            int artistCount = artistCountMap.getOrDefault(artistKey, 0);

            if (artistCount >= MAX_ARTIST_DUPLICATE) {
                continue;
            }

            usedTrackKeys.add(trackKey);
            artistCountMap.put(artistKey, artistCount + 1);
            result.add(copyTrackWithRank(track, result.size() + 1));
        }

        if (result.size() < resultLimit) {
            for (Map<String, Object> track : candidates) {
                if (result.size() >= resultLimit) {
                    break;
                }

                String trackKey = createTrackUniqueKey(track);

                if (trackKey.isBlank() || usedTrackKeys.contains(trackKey)) {
                    continue;
                }

                usedTrackKeys.add(trackKey);
                result.add(copyTrackWithRank(track, result.size() + 1));
            }
        }

        return result;
    }

    private Map<String, Object> copyTrackWithRank(Map<String, Object> track, int rank) {
        Map<String, Object> copiedTrack = new LinkedHashMap<>(track);
        copiedTrack.put("rank", rank);
        return copiedTrack;
    }

    private String createTrackUniqueKey(Map<String, Object> track) {
        String id = getStringValue(track, "id");

        if (!id.isBlank()) {
            return "id:" + id;
        }

        String title = normalizeText(getStringValue(track, "title"));
        String artist = normalizeText(getStringValue(track, "artist"));

        if (title.isBlank() && artist.isBlank()) {
            return "";
        }

        return title + ":" + artist;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return "";
        }

        Object value = map.get(key);

        if (value == null) {
            return "";
        }

        return String.valueOf(value);
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void addKeyword(Set<String> keywordSet, String keyword) {
        if (keyword == null) {
            return;
        }

        String trimmedKeyword = keyword.trim().replaceAll("\\s+", " ");

        if (trimmedKeyword.isBlank()) {
            return;
        }

        keywordSet.add(trimmedKeyword);
    }

    private String overrideIntentByUserText(String text, String currentIntent) {
        String normalizedText = normalizeText(text);

        if (containsAny(normalizedText, List.of(
                "신나는",
                "신나",
                "기분전환",
                "기분 전환",
                "텐션",
                "업되는",
                "밝은",
                "즐거운",
                "흥나는",
                "댄스"
        ))) {
            return "UPLIFTING";
        }

        if (containsAny(normalizedText, List.of(
                "위로",
                "위로해",
                "위로해줘",
                "괜찮아지고",
                "힘들어",
                "토닥",
                "따뜻한",
                "힐링"
        ))) {
            return "COMFORT";
        }

        if (containsAny(normalizedText, List.of(
                "차분",
                "잔잔",
                "진정",
                "잠",
                "잠들",
                "편안",
                "조용"
        ))) {
            return "CALM_DOWN";
        }

        if (containsAny(normalizedText, List.of(
                "운동",
                "빡센",
                "강한",
                "에너지",
                "달리기",
                "헬스"
        ))) {
            return "ENERGETIC";
        }

        return currentIntent;
    }

    private boolean containsAny(String text, List<String> words) {
        if (text == null || text.isBlank()) {
            return false;
        }

        for (String word : words) {
            if (word != null && !word.isBlank() && text.contains(normalizeText(word))) {
                return true;
            }
        }

        return false;
    }

    private String createMoodLabel(String originalMoodLabel, String emotion, String intent) {
        if ("SAD".equals(emotion) && "UPLIFTING".equals(intent)) {
            return "슬프지만 기분전환이 필요한 상태";
        }

        if ("SAD".equals(emotion) && "ENERGETIC".equals(intent)) {
            return "슬프지만 에너지를 끌어올리고 싶은 상태";
        }

        if ("SAD".equals(emotion) && "COMFORT".equals(intent)) {
            return "위로가 필요한 슬픔";
        }

        if ("SAD".equals(emotion) && "CALM_DOWN".equals(intent)) {
            return "차분하게 가라앉히고 싶은 슬픔";
        }

        if (originalMoodLabel != null && !originalMoodLabel.isBlank()) {
            return originalMoodLabel;
        }

        return switch (emotion) {
            case "HAPPY" -> "기분 좋은 상태";
            case "SAD" -> "슬픈 상태";
            case "ANGRY" -> "화가 난 상태";
            case "CALM" -> "차분한 상태";
            case "TIRED" -> "지친 상태";
            case "ANXIOUS" -> "불안한 상태";
            case "ROMANTIC" -> "설레는 상태";
            default -> "평범한 상태";
        };
    }

    private String createReason(String originalReason, String emotion, String intent) {
        if ("SAD".equals(emotion) && "UPLIFTING".equals(intent)) {
            return "슬픈 감정은 있지만 신나는 음악으로 분위기를 바꾸고 싶어하는 문장으로 판단했어요.";
        }

        if ("SAD".equals(emotion) && "COMFORT".equals(intent)) {
            return "슬픈 감정을 해소하기보다 따뜻한 위로를 원하는 문장으로 판단했어요.";
        }

        if ("SAD".equals(emotion) && "MATCH_MOOD".equals(intent)) {
            return "슬픈 감정을 그대로 담아낼 수 있는 음악을 원하는 문장으로 판단했어요.";
        }

        if (originalReason != null && !originalReason.isBlank()) {
            return originalReason;
        }

        return "입력한 문장의 감정과 추천 방향을 함께 분석했어요.";
    }

    private List<String> getDefaultKeywordsByIntent(String intent) {
        return switch (intent) {
            case "MATCH_MOOD" -> List.of(
                    "sad Korean ballad",
                    "emotional Korean song",
                    "Korean indie sad",
                    "rainy day Korean music",
                    "sad K-pop"
            );

            case "UPLIFTING" -> List.of(
                    "feel good K-pop",
                    "upbeat Korean pop",
                    "bright K-pop",
                    "dance pop Korea",
                    "happy Korean song",
                    "summer K-pop"
            );

            case "COMFORT" -> List.of(
                    "comfort Korean ballad",
                    "healing Korean song",
                    "warm acoustic Korean",
                    "soft Korean ballad",
                    "calm emotional Korean song",
                    "comfort K-pop"
            );

            case "CALM_DOWN" -> List.of(
                    "calm Korean acoustic",
                    "lofi Korean music",
                    "soft chill Korean",
                    "peaceful Korean song",
                    "relaxing Korean music"
            );

            case "ENERGETIC" -> List.of(
                    "energetic K-pop",
                    "K-pop dance",
                    "workout Korean pop",
                    "powerful K-pop",
                    "high energy pop Korea"
            );

            case "FOCUS" -> List.of(
                    "lofi beats",
                    "study music Korea",
                    "focus playlist",
                    "calm instrumental",
                    "chillhop"
            );

            case "ROMANTIC" -> List.of(
                    "romantic Korean song",
                    "love K-pop",
                    "Korean R&B love",
                    "sweet Korean ballad",
                    "romantic acoustic"
            );

            default -> List.of(
                    "K-pop",
                    "Korean pop",
                    "Korean indie",
                    "popular Korean songs",
                    "mood playlist"
            );
        };
    }

    private List<String> getDefaultKeywordsByEmotion(String emotion) {
        return switch (emotion) {
            case "HAPPY" -> List.of(
                    "happy K-pop",
                    "bright Korean pop",
                    "feel good Korean song"
            );

            case "SAD" -> List.of(
                    "sad Korean ballad",
                    "emotional Korean song",
                    "Korean indie ballad"
            );

            case "ANGRY" -> List.of(
                    "rock Korean",
                    "powerful K-pop",
                    "intense pop"
            );

            case "CALM" -> List.of(
                    "calm Korean song",
                    "acoustic Korean",
                    "chill Korean music"
            );

            case "TIRED" -> List.of(
                    "healing Korean song",
                    "rest Korean music",
                    "soft Korean acoustic"
            );

            case "ANXIOUS" -> List.of(
                    "calming Korean music",
                    "comfort Korean ballad",
                    "peaceful acoustic"
            );

            case "ROMANTIC" -> List.of(
                    "romantic Korean ballad",
                    "Korean love song",
                    "Korean R&B"
            );

            default -> List.of(
                    "Korean pop",
                    "K-pop",
                    "mood music"
            );
        };
    }
}