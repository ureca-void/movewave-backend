package com.example.demo.service;

import com.example.demo.dto.EmotionAnalyzeResponse;
import com.example.demo.dto.EmotionRecommendResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class EmotionRecommendService {

    private static final int MAX_RECOMMEND_TRACKS = 10;
    private static final int TRACKS_PER_KEYWORD = 5;
    private static final int MAX_AI_KEYWORDS = 4;

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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "감정 문장을 입력해주세요.");
        }

        EmotionAnalyzeResponse analysis = openAiEmotionService.analyzeEmotion(text.trim());

        List<String> searchKeywords = createRecommendationKeywords(analysis);

        List<Map<String, Object>> recommendedTracks = new ArrayList<>();
        Set<String> trackIds = new HashSet<>();

        for (String keyword : searchKeywords) {
            if (recommendedTracks.size() >= MAX_RECOMMEND_TRACKS) {
                break;
            }

            List<Map<String, Object>> tracks =
                    spotifyService.searchTracksForMood(keyword, TRACKS_PER_KEYWORD);

            for (Map<String, Object> track : tracks) {
                if (recommendedTracks.size() >= MAX_RECOMMEND_TRACKS) {
                    break;
                }

                String id = Objects.toString(track.get("id"), "");

                if (id.isBlank()) {
                    continue;
                }

                if (trackIds.contains(id)) {
                    continue;
                }

                trackIds.add(id);

                Map<String, Object> copiedTrack = new LinkedHashMap<>(track);
                copiedTrack.put("rank", recommendedTracks.size() + 1);
                copiedTrack.put("recommendKeyword", keyword);

                recommendedTracks.add(copiedTrack);
            }
        }

        return new EmotionRecommendResponse(
                analysis.emotion(),
                analysis.moodLabel(),
                analysis.reason(),
                searchKeywords,
                recommendedTracks
        );
    }

    private List<String> createRecommendationKeywords(EmotionAnalyzeResponse analysis) {
        LinkedHashSet<String> keywordSet = new LinkedHashSet<>();

        String emotion = analysis == null ? null : analysis.emotion();

        // 1. 감정별 기본 검색어를 먼저 사용
        // OpenAI 키워드만 믿으면 Spotify에서 엉뚱한 문자열 검색 결과가 나올 수 있음
        keywordSet.addAll(getDefaultKeywordsByEmotion(emotion));

        // 2. OpenAI가 뽑은 키워드는 보조 검색어로 사용
        if (analysis != null && analysis.keywords() != null) {
            int count = 0;

            for (String keyword : analysis.keywords()) {
                if (count >= MAX_AI_KEYWORDS) {
                    break;
                }

                String cleanedKeyword = cleanKeyword(keyword);

                if (cleanedKeyword.isBlank()) {
                    continue;
                }

                keywordSet.add(cleanedKeyword);
                count++;
            }
        }

        return new ArrayList<>(keywordSet);
    }

    private String cleanKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }

        return keyword
                .trim()
                .replaceAll("\\s+", " ");
    }

    private List<String> getDefaultKeywordsByEmotion(String emotion) {
        if (emotion == null) {
            return List.of(
                    "k-pop chill",
                    "korean pop",
                    "daily mix"
            );
        }

        return switch (emotion) {
            case "HAPPY" -> List.of(
                    "happy k-pop",
                    "feel good k-pop",
                    "dance pop"
            );

            case "SAD" -> List.of(
                    "korean ballad",
                    "sad ballad",
                    "emotional korean song"
            );

            case "ANGRY" -> List.of(
                    "rock workout",
                    "intense hip hop",
                    "powerful rock"
            );

            case "CALM" -> List.of(
                    "lofi chill",
                    "calm acoustic",
                    "soft pop"
            );

            case "TIRED" -> List.of(
                    "healing acoustic",
                    "soft korean ballad",
                    "lofi chill"
            );

            case "ANXIOUS" -> List.of(
                    "calm piano",
                    "relaxing acoustic",
                    "peaceful music"
            );

            case "ROMANTIC" -> List.of(
                    "romantic k-pop",
                    "r&b love",
                    "love ballad"
            );

            default -> List.of(
                    "k-pop chill",
                    "korean pop",
                    "daily mix"
            );
        };
    }
}