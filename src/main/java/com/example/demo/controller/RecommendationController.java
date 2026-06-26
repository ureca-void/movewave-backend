package com.example.demo.controller;

import com.example.demo.dto.TasteRecommendResponse;
import com.example.demo.dto.WeatherRecommendResponse;
import com.example.demo.service.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recommend")
public class RecommendationController {

    private static final int FULL_RECOMMENDATION_LIMIT = 100;

    private final RecommendationService recommendationService;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public RecommendationController(
            RecommendationService recommendationService,
            OAuth2AuthorizedClientManager authorizedClientManager
    ) {
        this.recommendationService = recommendationService;
        this.authorizedClientManager = authorizedClientManager;
    }

    @GetMapping("/weather")
    public WeatherRecommendResponse recommendByWeather(
            @RequestParam(defaultValue = "Rain") String weather,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int limit
    ) {
        WeatherRecommendResponse response =
                recommendationService.recommendByWeather(weather, FULL_RECOMMENDATION_LIMIT);

        return new WeatherRecommendResponse(
                response.weather(),
                response.moodLabel(),
                response.reason(),
                response.keywords(),
                sliceTracks(response.tracks(), page, limit)
        );
    }

    @GetMapping("/taste")
    public ResponseEntity<?> recommendByTaste(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int limit
    ) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Spotify 로그인이 필요합니다."));
        }

        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(oauthToken.getAuthorizedClientRegistrationId())
                .principal(authentication)
                .build();

        OAuth2AuthorizedClient client = authorizedClientManager.authorize(authorizeRequest);

        if (client == null || client.getAccessToken() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Spotify access token이 없습니다."));
        }

        TasteRecommendResponse response = recommendationService.recommendByTaste(
                client.getAccessToken().getTokenValue(),
                FULL_RECOMMENDATION_LIMIT
        );

        return ResponseEntity.ok(new TasteRecommendResponse(
                response.moodLabel(),
                response.reason(),
                response.dominantGenre(),
                response.keywords(),
                response.genreStats(),
                response.weatherStats(),
                response.sourceTracks(),
                sliceTracks(response.tracks(), page, limit)
        ));
    }

    private List<Map<String, Object>> sliceTracks(
            List<Map<String, Object>> tracks,
            int page,
            int limit
    ) {
        if (tracks == null || tracks.isEmpty()) {
            return List.of();
        }

        int safePage = Math.max(page, 0);
        int safeLimit = Math.min(Math.max(limit, 1), FULL_RECOMMENDATION_LIMIT);
        int fromIndex = safePage * safeLimit;

        if (fromIndex >= tracks.size()) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + safeLimit, tracks.size());

        return tracks.subList(fromIndex, toIndex);
    }
}
