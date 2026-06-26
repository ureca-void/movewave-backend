package com.example.demo.service;

import com.example.demo.dto.AiRecommendationProfile;
import com.example.demo.dto.TasteAnalysisResponse;
import com.example.demo.dto.TasteRecommendResponse;
import com.example.demo.dto.TasteStatItem;
import com.example.demo.dto.WeatherRecommendResponse;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Service
public class RecommendationService {

    private static final int DEFAULT_RESULT_LIMIT = 10;
    private static final int SEARCH_LIMIT_PER_KEYWORD = 6;
    private static final int MAX_KEYWORDS_TO_SEARCH = 12;
    private static final int MAX_ARTIST_DUPLICATE = 2;
    private static final int RECENTLY_PLAYED_LIMIT = 50;

    private static final Map<String, WeatherProfile> WEATHER_PROFILES = createWeatherProfiles();
    private static final Map<String, String> WEATHER_ALIASES = createWeatherAliases();

    private final OpenAiRecommendationService openAiRecommendationService;
    private final SpotifyService spotifyService;
    private final RestTemplate restTemplate;

    public RecommendationService(
            OpenAiRecommendationService openAiRecommendationService,
            SpotifyService spotifyService
    ) {
        this.openAiRecommendationService = openAiRecommendationService;
        this.spotifyService = spotifyService;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public WeatherRecommendResponse recommendByWeather(String weather, int limit) {
        int safeLimit = normalizeLimit(limit);
        WeatherProfile profile = getWeatherProfile(weather);

        AiRecommendationProfile aiProfile;

        try {
            aiProfile = openAiRecommendationService.analyzeWeather(
                    profile.weather(),
                    profile.title(),
                    profile.description(),
                    profile.genre()
            );
        } catch (Exception e) {
            aiProfile = createFallbackWeatherProfile(profile);
        }

        List<String> keywords = mergeKeywords(
                profile.keywords(),
                aiProfile.keywords()
        );

        List<Map<String, Object>> tracks = collectDiverseTracks(keywords, safeLimit);

        if (tracks.isEmpty()) {
            tracks = spotifyService.getPopularTracks(safeLimit);
        }

        return new WeatherRecommendResponse(
                profile.key(),
                fallbackText(aiProfile.moodLabel(), profile.title()),
                fallbackText(aiProfile.reason(), profile.description()),
                keywords,
                tracks
        );
    }

    public TasteRecommendResponse recommendByTaste(String userAccessToken, int limit) {
        int safeLimit = normalizeLimit(limit);

        List<Map<String, Object>> recentSpotifyTracks =
                fetchRecentlyPlayedSpotifyTracks(userAccessToken, RECENTLY_PLAYED_LIMIT);

        if (recentSpotifyTracks.isEmpty()) {
            recentSpotifyTracks = fetchCurrentSpotifyTrack(userAccessToken);
        }

        List<Map<String, Object>> sourceTracks = convertSpotifyTracksToCards(recentSpotifyTracks);

        if (sourceTracks.isEmpty()) {
            sourceTracks = spotifyService.getPopularTracks(DEFAULT_RESULT_LIMIT);
        }

        TasteAnalysisResponse analysis;

        try {
            analysis = openAiRecommendationService.analyzeTaste(sourceTracks);
        } catch (Exception e) {
            analysis = createFallbackTasteAnalysis(sourceTracks);
        }

        TasteAnalysisResponse normalizedAnalysis = normalizeTasteAnalysis(analysis, sourceTracks);

        List<String> keywords = mergeKeywords(
                createTasteBaseKeywords(normalizedAnalysis),
                normalizedAnalysis.keywords()
        );

        List<Map<String, Object>> tracks = collectDiverseTracks(keywords, safeLimit);

        if (tracks.isEmpty()) {
            tracks = spotifyService.getPopularTracks(safeLimit);
        }

        return new TasteRecommendResponse(
                normalizedAnalysis.moodLabel(),
                normalizedAnalysis.reason(),
                normalizedAnalysis.dominantGenre(),
                keywords,
                normalizedAnalysis.genreStats(),
                normalizedAnalysis.weatherStats(),
                sourceTracks,
                tracks
        );
    }

    private int normalizeLimit(int limit) {
        return Math.min(Math.max(limit, 1), DEFAULT_RESULT_LIMIT);
    }

    private WeatherProfile getWeatherProfile(String weather) {
        String normalizedWeather = normalizeKey(weather);
        String weatherKey = WEATHER_ALIASES.getOrDefault(normalizedWeather, normalizedWeather);

        return WEATHER_PROFILES.getOrDefault(weatherKey, WEATHER_PROFILES.get("Rain"));
    }

    private AiRecommendationProfile createFallbackWeatherProfile(WeatherProfile profile) {
        return new AiRecommendationProfile(
                profile.title(),
                profile.description(),
                profile.keywords()
        );
    }

    private TasteAnalysisResponse normalizeTasteAnalysis(
            TasteAnalysisResponse analysis,
            List<Map<String, Object>> sourceTracks
    ) {
        TasteAnalysisResponse fallback = createFallbackTasteAnalysis(sourceTracks);

        return new TasteAnalysisResponse(
                fallbackText(analysis.moodLabel(), fallback.moodLabel()),
                fallbackText(analysis.reason(), fallback.reason()),
                fallbackText(analysis.dominantGenre(), fallback.dominantGenre()),
                normalizeKeywords(analysis.keywords(), fallback.keywords()),
                normalizeStats(analysis.genreStats(), fallback.genreStats()),
                normalizeStats(analysis.weatherStats(), fallback.weatherStats())
        );
    }

    private TasteAnalysisResponse createFallbackTasteAnalysis(List<Map<String, Object>> sourceTracks) {
        Map<String, Integer> genreCountMap = new LinkedHashMap<>();
        Map<String, Integer> weatherCountMap = new LinkedHashMap<>();

        for (Map<String, Object> track : sourceTracks) {
            String text = "%s %s".formatted(
                    getStringValue(track, "title"),
                    getStringValue(track, "artist")
            );

            String genre = inferGenre(text);
            String weather = inferWeather(genre, text);

            genreCountMap.merge(genre, 1, Integer::sum);
            weatherCountMap.merge(weather, 1, Integer::sum);
        }

        if (genreCountMap.isEmpty()) {
            genreCountMap.put("Pop", 1);
            weatherCountMap.put("Sunny", 1);
        }

        List<TasteStatItem> genreStats = toStatItems(genreCountMap);
        List<TasteStatItem> weatherStats = toStatItems(weatherCountMap);
        String dominantGenre = genreStats.get(0).label();

        return new TasteAnalysisResponse(
                dominantGenre + " 중심 취향",
                "최근 재생 곡에서 " + dominantGenre + " 계열의 비중이 높게 나타났습니다.",
                dominantGenre,
                List.of(
                        dominantGenre + " Korean music",
                        dominantGenre + " playlist",
                        "Korean " + dominantGenre,
                        "K-pop " + dominantGenre,
                        "Korean R&B",
                        "Korean indie",
                        "mood playlist",
                        "chill Korean music"
                ),
                genreStats,
                weatherStats
        );
    }

    private List<TasteStatItem> normalizeStats(
            List<TasteStatItem> stats,
            List<TasteStatItem> fallbackStats
    ) {
        if (stats == null || stats.isEmpty()) {
            return fallbackStats;
        }

        return stats.stream()
                .filter(stat -> stat != null)
                .filter(stat -> stat.label() != null && !stat.label().isBlank())
                .filter(stat -> stat.count() > 0)
                .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                .limit(6)
                .toList();
    }

    private List<TasteStatItem> toStatItems(Map<String, Integer> countMap) {
        return countMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(6)
                .map(entry -> new TasteStatItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<String> createTasteBaseKeywords(TasteAnalysisResponse analysis) {
        String dominantGenre = fallbackText(analysis.dominantGenre(), "Pop");
        String moodLabel = fallbackText(analysis.moodLabel(), "mood");

        return List.of(
                dominantGenre + " Korean music",
                "Korean " + dominantGenre,
                dominantGenre + " playlist",
                moodLabel + " music"
        );
    }

    private List<String> mergeKeywords(List<String> primaryKeywords, List<String> secondaryKeywords) {
        LinkedHashSet<String> keywordSet = new LinkedHashSet<>();

        addKeywords(keywordSet, primaryKeywords);
        addKeywords(keywordSet, secondaryKeywords);

        return keywordSet.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .limit(MAX_KEYWORDS_TO_SEARCH)
                .toList();
    }

    private List<String> normalizeKeywords(List<String> keywords, List<String> fallbackKeywords) {
        List<String> normalizedKeywords = mergeKeywords(keywords, fallbackKeywords);

        if (normalizedKeywords.isEmpty()) {
            return fallbackKeywords;
        }

        return normalizedKeywords;
    }

    private void addKeywords(LinkedHashSet<String> keywordSet, List<String> keywords) {
        if (keywords == null) {
            return;
        }

        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }

            keywordSet.add(keyword.trim());
        }
    }

    private List<Map<String, Object>> collectDiverseTracks(List<String> keywords, int resultLimit) {
        Map<String, Map<String, Object>> candidateMap = new LinkedHashMap<>();

        for (String keyword : keywords.stream().limit(MAX_KEYWORDS_TO_SEARCH).toList()) {
            List<Map<String, Object>> searchedTracks =
                    spotifyService.searchTracksForMood(keyword, SEARCH_LIMIT_PER_KEYWORD);

            for (Map<String, Object> track : searchedTracks) {
                String trackKey = createTrackUniqueKey(track);

                if (trackKey.isBlank()) {
                    continue;
                }

                candidateMap.putIfAbsent(trackKey, track);
            }
        }

        List<Map<String, Object>> candidates = new ArrayList<>(candidateMap.values());
        Map<String, Integer> artistCountMap = new HashMap<>();
        Set<String> usedTrackKeys = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> candidate : candidates) {
            if (result.size() >= resultLimit) {
                break;
            }

            String trackKey = createTrackUniqueKey(candidate);
            String artistKey = normalizeText(getStringValue(candidate, "artist"));
            int artistCount = artistCountMap.getOrDefault(artistKey, 0);

            if (usedTrackKeys.contains(trackKey) || artistCount >= MAX_ARTIST_DUPLICATE) {
                continue;
            }

            usedTrackKeys.add(trackKey);
            artistCountMap.put(artistKey, artistCount + 1);
            result.add(copyTrackWithRank(candidate, result.size() + 1));
        }

        if (result.size() < resultLimit) {
            for (Map<String, Object> candidate : candidates) {
                if (result.size() >= resultLimit) {
                    break;
                }

                String trackKey = createTrackUniqueKey(candidate);

                if (usedTrackKeys.contains(trackKey)) {
                    continue;
                }

                usedTrackKeys.add(trackKey);
                result.add(copyTrackWithRank(candidate, result.size() + 1));
            }
        }

        return result;
    }

    private Map<String, Object> copyTrackWithRank(Map<String, Object> track, int rank) {
        Map<String, Object> copiedTrack = new LinkedHashMap<>(track);
        copiedTrack.put("rank", rank);

        return copiedTrack;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchRecentlyPlayedSpotifyTracks(
            String userAccessToken,
            int limit
    ) {
        if (userAccessToken == null || userAccessToken.isBlank()) {
            return List.of();
        }

        try {
            String url = UriComponentsBuilder
                    .fromUriString("https://api.spotify.com/v1/me/player/recently-played")
                    .queryParam("limit", Math.min(Math.max(limit, 1), 50))
                    .encode()
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    createSpotifyRequest(userAccessToken),
                    Map.class
            );

            Map<String, Object> body = response.getBody();

            if (body == null) {
                return List.of();
            }

            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) body.get("items");

            if (items == null || items.isEmpty()) {
                return List.of();
            }

            List<Map<String, Object>> tracks = new ArrayList<>();

            for (Map<String, Object> item : items) {
                Map<String, Object> track = (Map<String, Object>) item.get("track");

                if (track != null && !track.isEmpty()) {
                    tracks.add(track);
                }
            }

            return tracks;
        } catch (HttpStatusCodeException e) {
            System.out.println("Spotify 최근 재생 이력 조회 실패 상태 코드: " + e.getStatusCode());
            System.out.println("Spotify 최근 재생 이력 조회 실패 응답: " + e.getResponseBodyAsString());

            return List.of();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchCurrentSpotifyTrack(String userAccessToken) {
        if (userAccessToken == null || userAccessToken.isBlank()) {
            return List.of();
        }

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.spotify.com/v1/me/player/currently-playing?market=KR",
                    HttpMethod.GET,
                    createSpotifyRequest(userAccessToken),
                    Map.class
            );

            Map<String, Object> body = response.getBody();

            if (body == null) {
                return List.of();
            }

            Map<String, Object> item = (Map<String, Object>) body.get("item");

            if (item == null || item.isEmpty()) {
                return List.of();
            }

            return List.of(item);
        } catch (Exception e) {
            return List.of();
        }
    }

    private HttpEntity<Void> createSpotifyRequest(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        return new HttpEntity<>(headers);
    }

    private List<Map<String, Object>> convertSpotifyTracksToCards(
            List<Map<String, Object>> spotifyTracks
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> usedTrackIds = new HashSet<>();

        for (Map<String, Object> spotifyTrack : spotifyTracks) {
            String id = getStringValue(spotifyTrack, "id");

            if (id.isBlank() || usedTrackIds.contains(id)) {
                continue;
            }

            usedTrackIds.add(id);
            result.add(convertSpotifyTrackToCard(spotifyTrack, result.size() + 1));
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertSpotifyTrackToCard(
            Map<String, Object> track,
            int rank
    ) {
        String id = getStringValue(track, "id");
        String uri = getStringValue(track, "uri");
        String title = getStringValue(track, "name");

        Number durationMsValue = (Number) track.get("duration_ms");
        int durationMs = durationMsValue == null ? 0 : durationMsValue.intValue();

        List<Map<String, Object>> artists =
                (List<Map<String, Object>>) track.get("artists");

        String artistName = "Unknown Artist";

        if (artists != null && !artists.isEmpty()) {
            artistName = Objects.toString(artists.get(0).get("name"), "Unknown Artist");
        }

        Map<String, Object> album =
                (Map<String, Object>) track.get("album");

        String albumName = "";
        String releaseDate = "";
        String cover = "";

        if (album != null) {
            albumName = Objects.toString(album.get("name"), "");
            releaseDate = Objects.toString(album.get("release_date"), "");

            List<Map<String, Object>> images =
                    (List<Map<String, Object>>) album.get("images");

            if (images != null && !images.isEmpty()) {
                cover = Objects.toString(images.get(0).get("url"), "");
            }
        }

        String genre = inferGenre(title + " " + artistName);
        String weather = inferWeather(genre, title + " " + artistName);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        card.put("musicId", id);
        card.put("uri", uri);
        card.put("rank", rank);
        card.put("title", title);
        card.put("description", artistName);
        card.put("artist", artistName);
        card.put("albumName", albumName);
        card.put("cover", cover);
        card.put("releaseDate", releaseDate);
        card.put("durationMs", durationMs);
        card.put("genre", genre);
        card.put("weather", weather);

        return card;
    }

    private String inferGenre(String text) {
        String normalizedText = normalizeText(text);

        if (containsAny(normalizedText, "bts", "blackpink", "nct", "ive", "newjeans", "illit", "aespa", "k-pop", "kpop")) {
            return "K-pop";
        }

        if (containsAny(normalizedText, "hip hop", "hip-hop", "rap", "carti", "haon", "bewhy")) {
            return "Hip-hop";
        }

        if (containsAny(normalizedText, "r&b", "rnb", "daniel caesar", "yerin baek", "dean")) {
            return "R&B";
        }

        if (containsAny(normalizedText, "indie", "beach house", "cigarettes after sex", "black skirts", "se so neon")) {
            return "Indie";
        }

        if (containsAny(normalizedText, "rock", "band", "dragon pony")) {
            return "Rock";
        }

        if (containsAny(normalizedText, "jazz", "laufey")) {
            return "Jazz";
        }

        if (containsAny(normalizedText, "piano", "classic", "classical")) {
            return "Classical";
        }

        if (containsAny(normalizedText, "ballad", "iu", "davichi")) {
            return "Ballad";
        }

        if (containsAny(normalizedText, "electronic", "edm", "synth", "dance")) {
            return "Electronic";
        }

        return "Pop";
    }

    private String inferWeather(String genre, String text) {
        String normalizedText = normalizeText(text);

        if (containsAny(normalizedText, "rain", "night", "sad", "blue", "tears")) {
            return "Rainy";
        }

        if (containsAny(normalizedText, "snow", "winter", "white")) {
            return "Snowy";
        }

        if (containsAny(normalizedText, "storm", "thunder", "dark", "rage")) {
            return "Stormy";
        }

        if (containsAny(normalizedText, "mist", "fog", "dream", "midnight")) {
            return "Foggy";
        }

        if (List.of("Ballad", "R&B", "Jazz", "Classical").contains(genre)) {
            return "Rainy";
        }

        if (List.of("Indie", "Electronic").contains(genre)) {
            return "Cloudy";
        }

        return "Sunny";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private String createTrackUniqueKey(Map<String, Object> track) {
        String id = getStringValue(track, "id");

        if (!id.isBlank()) {
            return id;
        }

        String title = normalizeText(getStringValue(track, "title"));
        String artist = normalizeText(getStringValue(track, "artist"));

        if (title.isBlank() || artist.isBlank()) {
            return "";
        }

        return title + "|" + artist;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return "";
        }

        return Objects.toString(map.get(key), "");
    }

    private String fallbackText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeKey(String text) {
        return normalizeText(text).replaceAll("\\s+", "");
    }

    private static Map<String, WeatherProfile> createWeatherProfiles() {
        Map<String, WeatherProfile> profiles = new LinkedHashMap<>();

        profiles.put("rain", new WeatherProfile(
                "Rain",
                "Rainy",
                "Overcast Vibes",
                "비 오는 날의 차분한 감정선을 따라가는 추천입니다.",
                "Lo-fi • Jazz • Acoustic",
                List.of(
                        "rainy day Korean ballad",
                        "Korean R&B rainy day",
                        "lofi rainy playlist",
                        "Korean indie acoustic",
                        "soft jazz rainy day",
                        "IU ballad",
                        "Yerin Baek rainy",
                        "calm Korean music"
                )
        ));

        profiles.put("clouds", new WeatherProfile(
                "Clouds",
                "Cloudy",
                "Cloudy Skies",
                "흐린 날의 몽환적이고 부드러운 분위기에 맞춘 추천입니다.",
                "Dream Pop • Indie • Shoegaze",
                List.of(
                        "cloudy Korean indie",
                        "dream pop Korea",
                        "shoegaze playlist",
                        "soft Korean band",
                        "cloudy day music",
                        "Korean indie rock",
                        "mellow K-pop",
                        "dreamy Korean music"
                )
        ));

        profiles.put("clear", new WeatherProfile(
                "Clear",
                "Sunny",
                "Golden Hour",
                "맑은 날의 밝고 경쾌한 에너지를 살리는 추천입니다.",
                "Pop • Funk • Disco",
                List.of(
                        "sunny K-pop",
                        "bright Korean pop",
                        "feel good pop",
                        "Korean funk",
                        "disco pop playlist",
                        "summer Korean music",
                        "upbeat K-pop",
                        "happy pop Korea"
                )
        ));

        profiles.put("snow", new WeatherProfile(
                "Snow",
                "Snowy",
                "Winter Hush",
                "눈 오는 날의 포근하고 조용한 무드에 맞춘 추천입니다.",
                "Piano • New Age • Classical",
                List.of(
                        "winter Korean ballad",
                        "snowy piano music",
                        "warm acoustic Korea",
                        "Korean winter playlist",
                        "soft classical piano",
                        "Korean ballad winter",
                        "cozy Korean music",
                        "new age piano"
                )
        ));

        profiles.put("thunderstorm", new WeatherProfile(
                "Thunderstorm",
                "Stormy",
                "Thunder Echoes",
                "강한 날씨에 어울리는 몰입감 있고 에너지 있는 추천입니다.",
                "Electronic • Alt Rock • Synthwave",
                List.of(
                        "stormy electronic",
                        "Korean alt rock",
                        "dark synthwave",
                        "powerful K-pop",
                        "energetic Korean music",
                        "electronic rock playlist",
                        "intense hip hop Korea",
                        "dark pop playlist"
                )
        ));

        profiles.put("foggy", new WeatherProfile(
                "Foggy",
                "Foggy",
                "Midnight Mist",
                "안개 낀 날의 몽환적이고 느린 감각에 맞춘 추천입니다.",
                "Trip Hop • Downtempo • Chillout",
                List.of(
                        "foggy downtempo",
                        "trip hop playlist",
                        "Korean chillout",
                        "midnight Korean R&B",
                        "dreamy lo-fi",
                        "ambient Korean music",
                        "slow Korean indie",
                        "moody playlist"
                )
        ));

        return profiles;
    }

    private static Map<String, String> createWeatherAliases() {
        Map<String, String> aliases = new HashMap<>();

        aliases.put("rain", "rain");
        aliases.put("rainy", "rain");
        aliases.put("cloud", "clouds");
        aliases.put("clouds", "clouds");
        aliases.put("cloudy", "clouds");
        aliases.put("clear", "clear");
        aliases.put("sunny", "clear");
        aliases.put("snow", "snow");
        aliases.put("snowy", "snow");
        aliases.put("thunderstorm", "thunderstorm");
        aliases.put("storm", "thunderstorm");
        aliases.put("stormy", "thunderstorm");
        aliases.put("mist", "foggy");
        aliases.put("fog", "foggy");
        aliases.put("foggy", "foggy");
        aliases.put("haze", "foggy");

        return aliases;
    }

    private record WeatherProfile(
            String key,
            String weather,
            String title,
            String description,
            String genre,
            List<String> keywords
    ) {
    }
}
