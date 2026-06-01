package com.example.demo.dto;

import java.util.List;
import java.util.Map;

public record EmotionRecommendResponse(
        String emotion,
        String moodLabel,
        String reason,
        List<String> keywords,
        List<Map<String, Object>> tracks
) {
}