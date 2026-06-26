package com.example.demo.dto;

import java.util.List;

public record AiRecommendationProfile(
        String moodLabel,
        String reason,
        List<String> keywords
) {
}
