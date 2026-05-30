package com.example.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@CrossOrigin(
        origins = {
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        },
        allowCredentials = "true"
)
public class SpotifyTokenController {

    @GetMapping("/api/spotify/access-token")
    public ResponseEntity<?> getAccessToken(
            @RegisteredOAuth2AuthorizedClient("spotify") OAuth2AuthorizedClient authorizedClient
    ) {
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