package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class SpotifyService {

    @Value("${spring.security.oauth2.client.registration.spotify.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.spotify.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate;

    private String cachedAccessToken;
    private long tokenExpiredAt = 0;

    public SpotifyService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);

        this.restTemplate = new RestTemplate(factory);
    }

    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis();

        if (cachedAccessToken != null && now < tokenExpiredAt) {
            return cachedAccessToken;
        }

        String tokenUrl = "https://accounts.spotify.com/api/token";

        String auth = clientId + ":" + clientSecret;
        String encodedAuth = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + encodedAuth);

        HttpEntity<String> request = new HttpEntity<>(
                "grant_type=client_credentials",
                headers
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenUrl,
                    request,
                    Map.class
            );

            Map body = response.getBody();

            if (body == null || body.get("access_token") == null) {
                throw new RuntimeException("Spotify access token 발급 실패");
            }

            cachedAccessToken = body.get("access_token").toString();

            Number expiresIn = (Number) body.get("expires_in");
            int expiresSeconds = expiresIn == null ? 3600 : expiresIn.intValue();

            tokenExpiredAt = now + ((expiresSeconds - 60) * 1000L);

            return cachedAccessToken;

        } catch (HttpStatusCodeException e) {
            System.out.println("Spotify 토큰 발급 실패 상태 코드: " + e.getStatusCode());
            System.out.println("Spotify 토큰 발급 실패 응답: " + e.getResponseBodyAsString());
            throw new RuntimeException("Spotify 토큰 발급 실패", e);
        }
    }

    // =========================
    // Ureca's Pick
    // 고정 데이터
    // =========================
    public List<Map<String, Object>> getUrecaPicks() {
        return List.of(
        		Map.of(
                        "id", "u1",
                        "title", "404 (New Era)",
                        "description", "KiiiKiii",
                        "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e028c77cd2419fcdb44198262a3"
                ),
        		Map.of(
                        "id", "u2",
                        "title", "Way Back Home",
                        "description", "SHAUN",
                        "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e029bb453695e0776ceb13576f3"
                ),
        		Map.of(
                        "id", "u3",
                        "title", "Cosmic",
                        "description", "Red Velvet",
                        "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e0233f4f800b259791768d04f40"
                ),
        		Map.of(
                        "id", "u4",
                        "title", "toxic till the end",
                        "description", "ROSÉ",
                        "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e02a9fb6e00986e42ad4764b1f3"
                ),
        		Map.of(
                        "id", "u5",
                        "title", "사랑하게 될 거야",
                        "description", "한로로",
                        "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e02041c2df6c6417db26f4133d4"
                ),
        		Map.of(
                        "id", "u6",
                        "title", "타임캡슐",
                        "description", "다비치",
                        "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e0242f191e863eb9f2c9325a6e6"
                )
        );
    }

    // =========================
    // Popular
    // popularity 높은 순서 6개
    // =========================
    public List<Map<String, Object>> getPopularTracks(int displayLimit) {
        return searchTracks("pop", 10, displayLimit);
    }

    public List<Map<String, Object>> getPopularKpopTracks(int displayLimit) {
        return getPopularTracks(displayLimit);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchTracks(String keyword, int searchLimit, int displayLimit) {
        String accessToken = getAccessToken();

        int safeDisplayLimit = Math.min(Math.max(displayLimit, 1), 10);
        int safeSearchLimit = Math.min(Math.max(searchLimit, safeDisplayLimit), 10);

        String url = UriComponentsBuilder
                .fromUriString("https://api.spotify.com/v1/search")
                .queryParam("q", keyword)
                .queryParam("type", "track")
                .queryParam("limit", safeSearchLimit)
                .encode()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    Map.class
            );

            Map<String, Object> body = response.getBody();

            if (body == null) {
                throw new RuntimeException("Spotify 검색 응답이 비어있습니다.");
            }

            Map<String, Object> tracks =
                    (Map<String, Object>) body.get("tracks");

            if (tracks == null) {
                throw new RuntimeException("Spotify tracks 데이터가 없습니다.");
            }

            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) tracks.get("items");

            if (items == null || items.isEmpty()) {
                return List.of();
            }

            items.sort((a, b) -> {
                Number popularityA = (Number) a.get("popularity");
                Number popularityB = (Number) b.get("popularity");

                int scoreA = popularityA == null ? 0 : popularityA.intValue();
                int scoreB = popularityB == null ? 0 : popularityB.intValue();

                return Integer.compare(scoreB, scoreA);
            });

            List<Map<String, Object>> result = new ArrayList<>();

            for (Map<String, Object> track : items) {
                if (result.size() >= safeDisplayLimit) {
                    break;
                }

                result.add(convertTrackToCard(track, result.size() + 1));
            }

            return result;

        } catch (HttpStatusCodeException e) {
            System.out.println("Spotify 인기 데이터 조회 실패 상태 코드: " + e.getStatusCode());
            System.out.println("Spotify 인기 데이터 조회 실패 응답: " + e.getResponseBodyAsString());
            throw new RuntimeException("Spotify 인기 데이터 조회 실패", e);
        }
    }

    // =========================
    // Latest
    // Search API에서 tag:new 앨범 검색 후 release_date 최신순 5개
    // =========================
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getLatestReleases(int displayLimit) {
        String accessToken = getAccessToken();

        int safeDisplayLimit = Math.min(Math.max(displayLimit, 1), 10);

        String url = UriComponentsBuilder
                .fromUriString("https://api.spotify.com/v1/search")
                .queryParam("q", "tag:new")
                .queryParam("type", "album")
                .queryParam("limit", 10)
                .encode()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    Map.class
            );

            Map<String, Object> body = response.getBody();

            if (body == null) {
                throw new RuntimeException("Spotify 최신 앨범 검색 응답이 비어있습니다.");
            }

            Map<String, Object> albums =
                    (Map<String, Object>) body.get("albums");

            if (albums == null) {
                throw new RuntimeException("Spotify albums 데이터가 없습니다.");
            }

            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) albums.get("items");

            if (items == null || items.isEmpty()) {
                return List.of();
            }

            items.sort((a, b) -> {
                String dateA = Objects.toString(a.get("release_date"), "");
                String dateB = Objects.toString(b.get("release_date"), "");

                return dateB.compareTo(dateA);
            });

            List<Map<String, Object>> result = new ArrayList<>();

            for (Map<String, Object> album : items) {
                if (result.size() >= safeDisplayLimit) {
                    break;
                }

                result.add(convertAlbumToCard(album, result.size() + 1));
            }

            return result;

        } catch (HttpStatusCodeException e) {
            System.out.println("Spotify 최신 앨범 검색 실패 상태 코드: " + e.getStatusCode());
            System.out.println("Spotify 최신 앨범 검색 실패 응답: " + e.getResponseBodyAsString());
            throw new RuntimeException("Spotify 최신 앨범 검색 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertTrackToCard(Map<String, Object> track, int rank) {
        String id = Objects.toString(track.get("id"), "");
        String title = Objects.toString(track.get("name"), "Unknown Title");

        List<Map<String, Object>> artists =
                (List<Map<String, Object>>) track.get("artists");

        String artistName = "Unknown Artist";

        if (artists != null && !artists.isEmpty()) {
            artistName = Objects.toString(artists.get(0).get("name"), "Unknown Artist");
        }

        Map<String, Object> album =
                (Map<String, Object>) track.get("album");

        String cover = "";

        if (album != null) {
            List<Map<String, Object>> images =
                    (List<Map<String, Object>>) album.get("images");

            if (images != null && !images.isEmpty()) {
                cover = Objects.toString(images.get(0).get("url"), "");
            }
        }
        
     // Valence 값 (0.0 ~ 1.0)
        Number valence = (Number) track.getOrDefault("valence", 0.5);
        double v = valence.doubleValue();
        
        int hash = id.hashCode();
        double v1 = Math.abs(hash % 100) / 100.0;
     // 날씨 5단계 분류 로직
        String weather;
        if (v1 >= 0.8) weather = "Sunny";      // 매우 밝음
        else if (v1 >= 0.6) weather = "Cloudy"; // 보통
        else if (v1 >= 0.4) weather = "Foggy";  // 모호함
        else if (v1 >= 0.2) weather = "Rainy";  // 어두움
        else weather = "Stormy";               // 매우 어둡고 강렬함

        Number popularity = (Number) track.get("popularity");
        int popularityScore = popularity == null ? 0 : popularity.intValue();

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        card.put("rank", rank);
        card.put("title", title);
        card.put("description", artistName);
        card.put("cover", cover);
        card.put("popularity", popularityScore);
        card.put("weather", weather);

        return card;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertAlbumToCard(Map<String, Object> album, int rank) {
        String id = Objects.toString(album.get("id"), "");
        String title = Objects.toString(album.get("name"), "Unknown Title");
        String releaseDate = Objects.toString(album.get("release_date"), "");

        List<Map<String, Object>> artists =
                (List<Map<String, Object>>) album.get("artists");

        String artistName = "Unknown Artist";

        if (artists != null && !artists.isEmpty()) {
            artistName = Objects.toString(artists.get(0).get("name"), "Unknown Artist");
        }

        String cover = "";

        List<Map<String, Object>> images =
                (List<Map<String, Object>>) album.get("images");

        if (images != null && !images.isEmpty()) {
            cover = Objects.toString(images.get(0).get("url"), "");
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        card.put("rank", rank);
        card.put("title", title);
        card.put("description", artistName);
        card.put("cover", cover);
        card.put("releaseDate", releaseDate);

        return card;
    }
}