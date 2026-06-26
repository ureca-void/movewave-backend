package com.example.demo.service;

import com.example.demo.dto.TasteAnalysisResponse;
import com.example.demo.dto.TasteRecommendResponse;
import com.example.demo.dto.TasteStatItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    @Test
    void recommendsDominantGenreTracksWhenDominantGenreIsNotKpop() {
        OpenAiRecommendationService openAiRecommendationService = mock(OpenAiRecommendationService.class);
        SpotifyService spotifyService = mock(SpotifyService.class);
        RecommendationService recommendationService = new RecommendationService(
                openAiRecommendationService,
                spotifyService
        );

        when(spotifyService.getPopularTracks(100))
                .thenReturn(List.of(createTrack("source-indie", "Indie Source Song", "Indie Band")));

        when(openAiRecommendationService.analyzeTaste(anyList()))
                .thenReturn(new TasteAnalysisResponse(
                        "인디 중심 취향",
                        "최근 재생 곡에서 인디 계열의 비중이 높게 나타났습니다.",
                        "Indie",
                        List.of("K-pop Indie", "Indie playlist", "Korean indie", "Dream pop"),
                        List.of(new TasteStatItem("Indie", 8), new TasteStatItem("K-pop", 1)),
                        List.of(new TasteStatItem("Cloudy", 5), new TasteStatItem("Sunny", 3))
                ));

        when(spotifyService.searchTracksForMood(anyString(), anyInt()))
                .thenAnswer(invocation -> List.of(createTrack(
                        invocation.getArgument(0, String.class),
                        invocation.getArgument(0, String.class) + " song",
                        "Indie Artist"
                )));

        TasteRecommendResponse response = recommendationService.recommendByTaste("", 20);

        assertThat(response.dominantGenre()).isEqualTo("Indie");
        assertThat(response.keywords())
                .contains("popular Indie", "Indie hits", "Indie playlist")
                .doesNotContain("K-pop Indie", "NewJeans", "IVE", "aespa");

        verify(spotifyService, never()).searchTracksForMood("NewJeans", 10);
    }

    private Map<String, Object> createTrack(String id, String title, String artist) {
        return Map.of(
                "id", id,
                "musicId", id,
                "uri", "spotify:track:" + id,
                "title", title,
                "artist", artist,
                "description", artist,
                "albumName", title,
                "cover", "",
                "rank", 1
        );
    }
}
