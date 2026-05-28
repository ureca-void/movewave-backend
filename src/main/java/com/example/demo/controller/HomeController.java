package com.example.demo.controller;

import com.example.demo.service.SpotifyService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/home")
@CrossOrigin(origins = "http://localhost:5173")
public class HomeController {

    private final SpotifyService spotifyService;

    public HomeController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    @GetMapping
    public Map<String, Object> getHomeData() {

        List<Map<String, Object>> urecaPicks;
        List<Map<String, Object>> popular;
        List<Map<String, Object>> latest;

        try {
            urecaPicks = spotifyService.getUrecaPicks();
        } catch (Exception e) {
            System.out.println("Ureca's Pick 데이터 조회 실패. 기본 데이터를 반환합니다.");
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
                    ),
                    Map.of(
                            "id", "p6",
                            "rank", 6,
                            "title", "Popular Song 6",
                            "description", "Popular Artist",
                            "cover", "https://picsum.photos/seed/popular6/300/300",
                            "popularity", 0
                    )
            );
        }

        try {
            latest = spotifyService.getLatestReleases(5);
        } catch (Exception e) {
            System.out.println("Spotify 최신 발매 데이터 조회 실패. 더미데이터를 반환합니다.");
            e.printStackTrace();

            latest = List.of(
                    Map.of(
                            "id", "l1",
                            "rank", 1,
                            "title", "Latest Album 1",
                            "description", "New Artist",
                            "cover", "https://picsum.photos/seed/latest1/300/300",
                            "releaseDate", ""
                    ),
                    Map.of(
                            "id", "l2",
                            "rank", 2,
                            "title", "Latest Album 2",
                            "description", "New Artist",
                            "cover", "https://picsum.photos/seed/latest2/300/300",
                            "releaseDate", ""
                    ),
                    Map.of(
                            "id", "l3",
                            "rank", 3,
                            "title", "Latest Album 3",
                            "description", "New Artist",
                            "cover", "https://picsum.photos/seed/latest3/300/300",
                            "releaseDate", ""
                    ),
                    Map.of(
                            "id", "l4",
                            "rank", 4,
                            "title", "Latest Album 4",
                            "description", "New Artist",
                            "cover", "https://picsum.photos/seed/latest4/300/300",
                            "releaseDate", ""
                    ),
                    Map.of(
                            "id", "l5",
                            "rank", 5,
                            "title", "Latest Album 5",
                            "description", "New Artist",
                            "cover", "https://picsum.photos/seed/latest5/300/300",
                            "releaseDate", ""
                    )
            );
        }

        return Map.of(
                "midMixes", urecaPicks,
                "popular", popular,
                "latest", latest
        );
    }
}