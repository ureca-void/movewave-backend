package com.example.demo.controller;

import com.example.demo.service.SpotifyService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MusicController {

    private final SpotifyService spotifyService;

    // 생성자 주입
    public MusicController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    @GetMapping("/music")
    public String musicPage() {
        // 여기에 spotifyService를 사용해서 데이터를 가져오는 로직을 추가
        return "music"; // music.html 페이지로 이동
    }
}