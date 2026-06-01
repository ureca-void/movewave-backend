package com.example.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class SpotifyTokenController {

    private final OAuth2AuthorizedClientService authorizedClientService;

    public SpotifyTokenController(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    @GetMapping("/api/spotify/access-token")
    public ResponseEntity<?> getAccessToken(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of(
                    "message", "Spotify 로그인이 필요합니다."
            ));
        }

        OAuth2AuthorizedClient authorizedClient =
                authorizedClientService.loadAuthorizedClient(
                        "spotify",
                        authentication.getName()
                );

        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "message", "Spotify access token이 없습니다."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "accessToken", authorizedClient.getAccessToken().getTokenValue(),
                "expiresAt", authorizedClient.getAccessToken().getExpiresAt() != null
                        ? authorizedClient.getAccessToken().getExpiresAt().toString()
                        : Instant.now().plusSeconds(3600).toString()
        ));
    }
}