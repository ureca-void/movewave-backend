package com.example.demo.controller;

import com.example.demo.dto.EmotionAnalyzeRequest;
import com.example.demo.dto.EmotionAnalyzeResponse;
import com.example.demo.dto.EmotionRecommendResponse;
import com.example.demo.service.EmotionRecommendService;
import com.example.demo.service.OpenAiEmotionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/emotion")
@CrossOrigin(origins = {
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:4173",
        "http://127.0.0.1:4173"
})
public class EmotionController {

    private final OpenAiEmotionService openAiEmotionService;
    private final EmotionRecommendService emotionRecommendService;

    public EmotionController(
            OpenAiEmotionService openAiEmotionService,
            EmotionRecommendService emotionRecommendService
    ) {
        this.openAiEmotionService = openAiEmotionService;
        this.emotionRecommendService = emotionRecommendService;
    }

    @PostMapping("/analyze")
    public EmotionAnalyzeResponse analyze(@RequestBody EmotionAnalyzeRequest request) {
        return openAiEmotionService.analyzeEmotion(request.text());
    }

    @PostMapping("/recommend")
    public EmotionRecommendResponse recommend(@RequestBody EmotionAnalyzeRequest request) {
        return emotionRecommendService.recommend(request.text());
    }
}