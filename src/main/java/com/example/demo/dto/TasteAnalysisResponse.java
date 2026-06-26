package com.example.demo.dto;

import java.util.List;

public record TasteAnalysisResponse(
        String moodLabel,
        String reason,
        String dominantGenre,
        List<String> keywords,
        List<TasteStatItem> genreStats,
        List<TasteStatItem> weatherStats
) {
}
