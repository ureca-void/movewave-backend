package com.example.demo.dto;

import java.util.List;
import java.util.Map;

public record WeatherRecommendResponse(
        String weather,
        String moodLabel,
        String reason,
        List<String> keywords,
        List<Map<String, Object>> tracks
) {
}
