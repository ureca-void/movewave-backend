package com.example.demo.controller;

import com.example.demo.service.SpotifyService;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RequestMapping("/api")
@RestController
public class MusicController {

    private final SpotifyService spotifyService;

    // 생성자 주입
    public MusicController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }
    
 // [추가] 프론트에서 fetch('http://localhost:8080/api/music') 할 때 호출되는 곳
    @GetMapping("/music")
    public List<Map<String, Object>> getMusicData() {
        return spotifyService.getPopularTracks(20); 
    }

//    @GetMapping("/spotify/access-token")
//    public List<Map<String, Object>> getMusicStats() {
//        // 인기 곡 데이터를 가져와서 반환
//        return spotifyService.getPopularTracks(20); 
//    }
}