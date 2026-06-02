package com.example.demo.dto;

import java.util.List;

public record EmotionAnalyzeResponse(
        String emotion,
        String recommendationIntent,
        String moodLabel,
        String reason,
        List<String> keywords
) {
}