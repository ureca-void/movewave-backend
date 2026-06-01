package com.example.demo.dto;

import java.util.List;

public record EmotionAnalyzeResponse(
        String emotion,
        String moodLabel,
        String reason,
        List<String> keywords
) {
}