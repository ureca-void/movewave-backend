package com.example.demo.controller;

import com.example.demo.service.SpotifyService;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api")
@RestController
public class MusicController {

    private final SpotifyService spotifyService;

    public MusicController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    // 프론트에서 /api/music 요청 시 호출
    @GetMapping("/music")
    public List<Map<String, Object>> getMusicData() {
        return spotifyService.getPopularTracks(60);
    }
}