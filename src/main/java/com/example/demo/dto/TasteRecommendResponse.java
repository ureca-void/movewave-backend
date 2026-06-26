package com.example.demo.dto;

import java.util.List;
import java.util.Map;

public record TasteRecommendResponse(
        String moodLabel,
        String reason,
        String dominantGenre,
        List<String> keywords,
        List<TasteStatItem> genreStats,
        List<TasteStatItem> weatherStats,
        List<Map<String, Object>> sourceTracks,
        List<Map<String, Object>> tracks
) {
}
