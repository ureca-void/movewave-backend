package com.example.demo.controller;

import com.example.demo.service.SpotifyService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:4173",
        "http://127.0.0.1:4173"
})
public class HomeController {

    private final SpotifyService spotifyService;

    public HomeController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    // =========================
    // 홈 데이터 API
    // 예: /api/home
    // =========================
    @GetMapping("/home")
    public Map<String, Object> getHomeData() {

        List<Map<String, Object>> urecaPicks;
        List<Map<String, Object>> popular;
        List<Map<String, Object>> latest;

        try {
            urecaPicks = spotifyService.getUrecaPicks();
        } catch (Exception e) {
            System.out.println("Ureca's Pick 데이터 조회 실패. 기본 데이터를 반환합니다.");
            printSpotifyError(e);
            e.printStackTrace();

            urecaPicks = List.of(
                    Map.of(
                            "id", "u1",
                            "title", "404 (New Era)",
                            "description", "KiiiKiii",
                            "cover", ""
                    ),
                    Map.of(
                            "id", "u2",
                            "title", "Way Back Home",
                            "description", "SHAUN",
                            "cover", ""
                    ),
                    Map.of(
                            "id", "u3",
                            "title", "Cosmic",
                            "description", "Red Velvet",
                            "cover", ""
                    ),
                    Map.of(
                            "id", "u4",
                            "title", "toxic till the end",
                            "description", "ROSÉ",
                            "cover", ""
                    ),
                    Map.of(
                            "id", "u5",
                            "title", "사랑하게 될 거야",
                            "description", "한로로",
                            "cover", ""
                    ),
                    Map.of(
                            "id", "u6",
                            "title", "타임캡슐",
                            "description", "다비치",
                            "cover", ""
                    )
            );
        }

        try {
            popular = spotifyService.getPopularTracks(5);
        } catch (Exception e) {
            System.out.println("Spotify 인기 데이터 조회 실패. 더미데이터를 반환합니다.");
            printSpotifyError(e);
            e.printStackTrace();

            popular = List.of(
                    Map.of(
                            "id", "p1",
                            "rank", 1,
                            "title", "Popular Song 1",
                            "description", "Popular Artist",
                            "cover", "https://picsum.photos/seed/popular1/300/300",
                            "popularity", 0
                    ),
                    Map.of(
                            "id", "p2",
                            "rank", 2,
                            "title", "Popular Song 2",
                            "description", "Popular Artist",
                            "cover", "https://picsum.photos/seed/popular2/300/300",
                            "popularity", 0
                    ),
                    Map.of(
                            "id", "p3",
                            "rank", 3,
                            "title", "Popular Song 3",
                            "description", "Popular Artist",
                            "cover", "https://picsum.photos/seed/popular3/300/300",
                            "popularity", 0
                    ),
                    Map.of(
                            "id", "p4",
                            "rank", 4,
                            "title", "Popular Song 4",
                            "description", "Popular Artist",
                            "cover", "https://picsum.photos/seed/popular4/300/300",
                            "popularity", 0
                    ),
                    Map.of(
                            "id", "p5",
                            "rank", 5,
                            "title", "Popular Song 5",
                            "description", "Popular Artist",
                            "cover", "https://picsum.photos/seed/popular5/300/300",
                            "popularity", 0
                    )
            );
        }

        try {
            latest = spotifyService.getLatestReleases(5);
        } catch (Exception e) {
            System.out.println("Spotify 최신 발매 데이터 조회 실패.");
            printSpotifyError(e);
            e.printStackTrace();

            latest = List.of();
        }

        return Map.of(
                "midMixes", urecaPicks,
                "popular", popular,
                "latest", latest
        );
    }

    // =========================
    // Latest 무한 스크롤 API
    // 예: /api/latest?page=0&limit=10
    // =========================
    @GetMapping("/latest")
    public List<Map<String, Object>> getLatestTracks(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        int safePage = Math.max(page, 0);

        // 한 번에 최대 10개
        int safeLimit = Math.min(Math.max(limit, 1), 10);

        // 전체 최대 100개
        int maxTotal = 100;
        int start = safePage * safeLimit;

        if (start >= maxTotal) {
            return List.of();
        }

        try {
            // 전체 100개를 먼저 가져오고 releaseDate 기준 정렬된 상태에서 잘라서 반환
            List<Map<String, Object>> allLatest = spotifyService.getLatestReleases(maxTotal);

            if (start >= allLatest.size()) {
                return List.of();
            }

            int end = Math.min(start + safeLimit, allLatest.size());

            return allLatest.subList(start, end);
        } catch (Exception e) {
            System.out.println("Spotify Latest 페이지 데이터 조회 실패.");
            printSpotifyError(e);
            e.printStackTrace();

            return List.of();
        }
    }

    // =========================
    // Popular 무한 스크롤 API
    // 예: /api/popular?page=0&limit=10
    // =========================
    @GetMapping("/popular")
    public List<Map<String, Object>> getPopularTracks(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        int safePage = Math.max(page, 0);

        // 한 번에 최대 10개
        int safeLimit = Math.min(Math.max(limit, 1), 10);

        // 전체 최대 100개
        int maxTotal = 100;
        int start = safePage * safeLimit;

        if (start >= maxTotal) {
            return List.of();
        }

        try {
            // 전체 100개를 먼저 가져오고 popularity 기준 정렬된 상태에서 잘라서 반환
            List<Map<String, Object>> allPopular = spotifyService.getPopularTracks(maxTotal);

            if (start >= allPopular.size()) {
                return List.of();
            }

            int end = Math.min(start + safeLimit, allPopular.size());

            return allPopular.subList(start, end);
        } catch (Exception e) {
            System.out.println("Spotify Popular 페이지 데이터 조회 실패.");
            printSpotifyError(e);
            e.printStackTrace();

            return List.of();
        }
    }

    // =========================
    // 검색 API
    // 예: /api/home/search?keyword=박효신
    // =========================
    @GetMapping("/home/search")
    public List<Map<String, Object>> searchTracks(
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }

        try {
            return spotifyService.searchTracksByKeyword(keyword.trim(), 50);
        } catch (Exception e) {
            System.out.println("Spotify 검색 데이터 조회 실패.");
            printSpotifyError(e);
            e.printStackTrace();

            return List.of();
        }
    }

    // =========================
    // Spotify 에러 로그 출력 함수
    // =========================
    private void printSpotifyError(Exception e) {
        if (e instanceof HttpClientErrorException httpError) {
            System.out.println("Spotify Status: " + httpError.getStatusCode());
            System.out.println("Retry-After: " + httpError.getResponseHeaders().getFirst("Retry-After"));
            System.out.println("Spotify Error Body: " + httpError.getResponseBodyAsString());
        }
    }
}