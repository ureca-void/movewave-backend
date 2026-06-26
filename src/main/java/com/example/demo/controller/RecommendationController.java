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

import java.util.Map;

@RestController
@RequestMapping("/api/recommend")
public class RecommendationController {

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
            @RequestParam(defaultValue = "10") int limit
    ) {
        return recommendationService.recommendByWeather(weather, limit);
    }

    @GetMapping("/taste")
    public ResponseEntity<?> recommendByTaste(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int limit
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
                limit
        );

        return ResponseEntity.ok(response);
    }
}
