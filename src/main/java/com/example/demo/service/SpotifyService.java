package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class SpotifyService {

    @Value("${spring.security.oauth2.client.registration.spotify.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.spotify.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate;

    private String cachedAccessToken;
    private long tokenExpiredAt = 0;

    private final Map<String, CachedList> cache = new HashMap<>();
    private static final int SPOTIFY_SEARCH_LIMIT = 10;
    private static final int MAX_SEARCH_KEYWORDS = 6;
    private static final int MAX_PAGES_PER_KEYWORD = 2;
    private static final int MAX_SEARCH_CANDIDATES = 80;

    public SpotifyService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);

        this.restTemplate = new RestTemplate(factory);
    }

    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis();

        if (cachedAccessToken != null && now < tokenExpiredAt) {
            return cachedAccessToken;
        }

        String tokenUrl = "https://accounts.spotify.com/api/token";

        String auth = clientId + ":" + clientSecret;
        String encodedAuth = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + encodedAuth);

        HttpEntity<String> request = new HttpEntity<>(
                "grant_type=client_credentials",
                headers
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenUrl,
                    request,
                    Map.class
            );

            Map body = response.getBody();

            if (body == null || body.get("access_token") == null) {
                throw new RuntimeException("Spotify access token 발급 실패");
            }

            cachedAccessToken = body.get("access_token").toString();

            Number expiresIn = (Number) body.get("expires_in");
            int expiresSeconds = expiresIn == null ? 3600 : expiresIn.intValue();

            tokenExpiredAt = now + ((expiresSeconds - 60) * 1000L);

            return cachedAccessToken;

        } catch (HttpStatusCodeException e) {
            printSpotifyHttpError("Spotify 토큰 발급 실패", e);
            throw new RuntimeException("Spotify 토큰 발급 실패", e);
        }
    }

    // =========================
	// Ureca's Pick
	// 고정 데이터 - 실제 Spotify id / uri 포함
	// =========================
	public List<Map<String, Object>> getUrecaPicks() {
	    return List.of(
	            Map.of(
	                    "id", "24rDDbSlFY9OHrlJb48CRh",
	                    "uri", "spotify:track:24rDDbSlFY9OHrlJb48CRh",
	                    "rank", 1,
	                    "title", "404 (New Era)",
	                    "description", "KiiiKiii",
	                    "artist", "KiiiKiii",
	                    "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e028c77cd2419fcdb44198262a3"
	            ),
	            Map.of(
	                    "id", "3NxuezMdSLgt4OwHzBoUhL",
	                    "uri", "spotify:track:3NxuezMdSLgt4OwHzBoUhL",
	                    "rank", 2,
	                    "title", "Way Back Home",
	                    "description", "SHAUN",
	                    "artist", "SHAUN",
	                    "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e029bb453695e0776ceb13576f3"
	            ),
	            Map.of(
	                    "id", "49ciDis1ofgszcKXKh0Sqb",
	                    "uri", "spotify:track:49ciDis1ofgszcKXKh0Sqb",
	                    "rank", 3,
	                    "title", "Cosmic",
	                    "description", "Red Velvet",
	                    "artist", "Red Velvet",
	                    "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e0233f4f800b259791768d04f40"
	            ),
	            Map.of(
	                    "id", "1z5ebC9238uGoBgzYyvGpQ",
	                    "uri", "spotify:track:1z5ebC9238uGoBgzYyvGpQ",
	                    "rank", 4,
	                    "title", "toxic till the end",
	                    "description", "ROSÉ",
	                    "artist", "ROSÉ",
	                    "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e02a9fb6e00986e42ad4764b1f3"
	            ),
	            Map.of(
	                    "id", "3WvM2dIR9iIxMGNMP7WsNw",
	                    "uri", "spotify:track:3WvM2dIR9iIxMGNMP7WsNw",
	                    "rank", 5,
	                    "title", "사랑하게 될 거야",
	                    "description", "한로로",
	                    "artist", "한로로",
	                    "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e02041c2df6c6417db26f4133d4"
	            ),
	            Map.of(
	                    "id", "3CQw6HqsBu12wUj89vUQ5M",
	                    "uri", "spotify:track:3CQw6HqsBu12wUj89vUQ5M",
	                    "rank", 6,
	                    "title", "타임캡슐",
	                    "description", "다비치",
	                    "artist", "다비치",
	                    "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e0242f191e863eb9f2c9325a6e6"
	            )
	    );
	}
	
    // =========================
	// Popular
	// Spotify Search API를 여러 번 호출해서 최대 100개까지 조회
	// =========================
	public List<Map<String, Object>> getPopularTracks(int displayLimit) {
	    int safeDisplayLimit = Math.min(Math.max(displayLimit, 1), 100);
	    String cacheKey = "popular:" + safeDisplayLimit;
	
	    List<Map<String, Object>> cached = getCached(cacheKey);
	
	    if (cached != null) {
	        return cached;
	    }
	
	    try {
	        List<Map<String, Object>> result = searchTracks("pop", safeDisplayLimit, safeDisplayLimit);
	
	        putCache(cacheKey, result, 10 * 60 * 1000L);
	
	        return result;
	    } catch (RuntimeException e) {
	        List<Map<String, Object>> stale = getStaleCache(cacheKey);

	        if (stale != null) {
	            return stale;
	        }
	
	        throw e;
	    }
	}


	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> searchTracks(String keyword, int searchLimit, int displayLimit) {
	    String accessToken = getAccessToken();
	
	    int safeDisplayLimit = Math.min(Math.max(displayLimit, 1), 100);
	    int safeSearchLimit = Math.min(Math.max(searchLimit, safeDisplayLimit), 100);
	
	    HttpHeaders headers = new HttpHeaders();
	    headers.setBearerAuth(accessToken);
	
	    HttpEntity<Void> request = new HttpEntity<>(headers);
	
	    Map<String, Map<String, Object>> trackMap = new LinkedHashMap<>();
	
	    try {
	        int spotifyLimit = 10;
	
	        for (int offset = 0; offset < safeSearchLimit; offset += spotifyLimit) {
	            int requestLimit = Math.min(spotifyLimit, safeSearchLimit - offset);
	
	            String url = UriComponentsBuilder
	                    .fromUriString("https://api.spotify.com/v1/search")
	                    .queryParam("q", keyword)
	                    .queryParam("type", "track")
	                    .queryParam("market", "KR")
	                    .queryParam("limit", requestLimit)
	                    .queryParam("offset", offset)
	                    .encode()
	                    .toUriString();
	
	            ResponseEntity<Map> response = restTemplate.exchange(
	                    url,
	                    HttpMethod.GET,
	                    request,
	                    Map.class
	            );
	
	            Map<String, Object> body = response.getBody();
	
	            if (body == null) {
	                break;
	            }
	
	            Map<String, Object> tracks =
	                    (Map<String, Object>) body.get("tracks");
	
	            if (tracks == null) {
	                break;
	            }
	
	            List<Map<String, Object>> items =
	                    (List<Map<String, Object>>) tracks.get("items");
	
	            if (items == null || items.isEmpty()) {
	                break;
	            }
	
	            for (Map<String, Object> track : items) {
	                String id = Objects.toString(track.get("id"), "");
	
	                if (id.isBlank()) {
	                    continue;
	                }
	
	                trackMap.putIfAbsent(id, track);
	            }
	
	            if (items.size() < requestLimit) {
	                break;
	            }
	
	            if (trackMap.size() >= safeDisplayLimit) {
	                break;
	            }
	        }
	
	        if (trackMap.isEmpty()) {
	            return List.of();
	        }
	
	        List<Map<String, Object>> sortedTracks = new ArrayList<>(trackMap.values());
	
	        sortedTracks.sort((a, b) -> {
	            Number popularityA = (Number) a.get("popularity");
	            Number popularityB = (Number) b.get("popularity");
	
	            int scoreA = popularityA == null ? 0 : popularityA.intValue();
	            int scoreB = popularityB == null ? 0 : popularityB.intValue();
	
	            return Integer.compare(scoreB, scoreA);
	        });
	
	        List<Map<String, Object>> result = new ArrayList<>();
	
	        for (Map<String, Object> track : sortedTracks) {
	            if (result.size() >= safeDisplayLimit) {
	                break;
	            }
	
	            result.add(convertTrackToCard(track, result.size() + 1));
	        }
	
	        return result;
	
	    } catch (HttpStatusCodeException e) {
	        printSpotifyHttpError("Spotify 인기 데이터 조회 실패", e);
	        throw new RuntimeException("Spotify 인기 데이터 조회 실패", e);
	    }
	}
	private String getWeatherByTrackId(String id) {
	    if (id == null || id.isBlank()) {
	        return "Unknown";
	    }

	    String[] weathers = {
	            "Sunny",
	            "Cloudy",
	            "Foggy",
	            "Rainy",
	            "Stormy",
	            "Snowy"
	    };

	    int hash = Math.abs(id.hashCode());
	    int index = hash % weathers.length;

	    return weathers[index];
	}

    public List<Map<String, Object>> getPopularKpopTracks(int displayLimit) {
        return getPopularTracks(displayLimit);
    }

    // =========================
    // 실제 검색
    // 요청 수 줄이기 버전
    // 1. 아티스트 top-tracks 호출 안 함
    // 2. Search API만 사용
    // 3. 검색 후보 최대 4개
    // 4. 각 후보당 요청 1번
    // =========================
    public List<Map<String, Object>> searchTracksByKeyword(String keyword, int displayLimit) {
        String trimmedKeyword = keyword == null ? "" : keyword.trim();

        if (trimmedKeyword.isEmpty()) {
            return List.of();
        }

        int safeDisplayLimit = Math.min(Math.max(displayLimit, 1), 10);
        String cacheKey = "search:" + normalizeAliasKey(trimmedKeyword) + ":" + safeDisplayLimit;

        List<Map<String, Object>> cached = getCached(cacheKey);

        if (cached != null) {
            return cached;
        }

        String accessToken = getAccessToken();

        List<Map<String, Object>> result =
                searchTracksByKeywordFallback(trimmedKeyword, safeDisplayLimit, accessToken);

        putCache(cacheKey, result, 60 * 1000L);

        return result;
    }
    
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchTracksForMood(String keyword, int displayLimit) {
        String trimmedKeyword = keyword == null ? "" : keyword.trim();

        if (trimmedKeyword.isEmpty()) {
            return List.of();
        }

        int safeDisplayLimit = Math.min(Math.max(displayLimit, 1), 10);

        String cacheKey = "mood:" + normalizeAliasKey(trimmedKeyword) + ":" + safeDisplayLimit;

        List<Map<String, Object>> cached = getCached(cacheKey);

        if (cached != null) {
            return cached;
        }

        String accessToken = getAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        Map<String, Map<String, Object>> trackMap = new LinkedHashMap<>();

        try {
            int spotifyLimit = 10;
            int maxPages = 2;

            for (int page = 0; page < maxPages; page++) {
                int offset = page * spotifyLimit;

                String url = UriComponentsBuilder
                        .fromUriString("https://api.spotify.com/v1/search")
                        .queryParam("q", trimmedKeyword)
                        .queryParam("type", "track")
                        .queryParam("market", "KR")
                        .queryParam("limit", spotifyLimit)
                        .queryParam("offset", offset)
                        .encode()
                        .toUriString();

                ResponseEntity<Map> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        request,
                        Map.class
                );

                Map<String, Object> body = response.getBody();

                if (body == null) {
                    break;
                }

                Map<String, Object> tracks =
                        (Map<String, Object>) body.get("tracks");

                if (tracks == null) {
                    break;
                }

                List<Map<String, Object>> items =
                        (List<Map<String, Object>>) tracks.get("items");

                if (items == null || items.isEmpty()) {
                    break;
                }

                for (Map<String, Object> track : items) {
                    String id = Objects.toString(track.get("id"), "");

                    if (id.isBlank()) {
                        continue;
                    }

                    trackMap.putIfAbsent(id, track);
                }

                if (items.size() < spotifyLimit) {
                    break;
                }
            }

            if (trackMap.isEmpty()) {
                return List.of();
            }

            List<Map<String, Object>> sortedTracks = new ArrayList<>(trackMap.values());

            sortedTracks.sort((a, b) -> {
                int scoreA = calculateMoodSearchScore(a, trimmedKeyword);
                int scoreB = calculateMoodSearchScore(b, trimmedKeyword);

                return Integer.compare(scoreB, scoreA);
            });

            List<Map<String, Object>> result = new ArrayList<>();

            for (Map<String, Object> track : sortedTracks) {
                if (result.size() >= safeDisplayLimit) {
                    break;
                }

                result.add(convertTrackToCard(track, result.size() + 1));
            }

            putCache(cacheKey, result, 60 * 1000L);

            return result;

        } catch (HttpStatusCodeException e) {
            printSpotifyHttpError("Spotify 감정 추천 검색 실패", e);

            List<Map<String, Object>> stale = getStaleCache(cacheKey);

            if (stale != null) {
                return stale;
            }

            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private int calculateMoodSearchScore(Map<String, Object> track, String keyword) {
        String normalizedKeyword = normalizeSearchText(keyword);

        String title = normalizeSearchText(
                Objects.toString(track.get("name"), "")
        );

        List<Map<String, Object>> artists =
                (List<Map<String, Object>>) track.get("artists");

        StringBuilder artistBuilder = new StringBuilder();

        if (artists != null) {
            for (Map<String, Object> artist : artists) {
                artistBuilder.append(" ")
                        .append(Objects.toString(artist.get("name"), ""));
            }
        }

        String artistText = normalizeSearchText(artistBuilder.toString());

        Map<String, Object> album =
                (Map<String, Object>) track.get("album");

        String albumName = "";

        if (album != null) {
            albumName = normalizeSearchText(
                    Objects.toString(album.get("name"), "")
            );
        }

        int score = 0;

        List<String> words = Arrays.stream(normalizedKeyword.split("\\s+"))
                .filter(word -> !word.isBlank())
                .filter(word -> word.length() >= 2)
                .toList();

        for (String word : words) {
            if (title.contains(word)) {
                score += 300;
            }

            if (artistText.contains(word)) {
                score += 150;
            }

            if (albumName.contains(word)) {
                score += 120;
            }
        }

        Number popularity = (Number) track.get("popularity");
        int popularityScore = popularity == null ? 0 : popularity.intValue();

        score += popularityScore * 3;

        if (containsMoodBadVersionWord(title)) {
            score -= 1200;
        }

        if (containsMoodBadVersionWord(albumName)) {
            score -= 700;
        }

        return score;
    }

    private boolean containsMoodBadVersionWord(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        List<String> badWords = List.of(
                "karaoke",
                "instrumental",
                "inst",
                "sped up",
                "slowed",
                "remix",
                "cover",
                "tribute",
                "piano version",
                "nightcore",
                "8d",
                "tiktok version"
        );

        for (String word : badWords) {
            if (text.contains(word)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
	private List<Map<String, Object>> searchTracksByKeywordFallback(
	        String keyword,
	        int safeDisplayLimit,
	        String accessToken
	) {
	    List<String> searchKeywords = createSearchKeywords(keyword);
	
	    if (searchKeywords.isEmpty()) {
	        return List.of();
	    }
	
	    Map<String, Map<String, Object>> trackMap = new LinkedHashMap<>();
	
	    HttpHeaders headers = new HttpHeaders();
	    headers.setBearerAuth(accessToken);
	
	    HttpEntity<Void> request = new HttpEntity<>(headers);
	
	    searchLoop:
	    for (String searchKeyword : searchKeywords) {
	        for (int page = 0; page < MAX_PAGES_PER_KEYWORD; page++) {
	            int offset = page * SPOTIFY_SEARCH_LIMIT;
	
	            String url = UriComponentsBuilder
	                    .fromUriString("https://api.spotify.com/v1/search")
	                    .queryParam("q", searchKeyword)
	                    .queryParam("type", "track")
	                    .queryParam("market", "KR")
	                    .queryParam("limit", SPOTIFY_SEARCH_LIMIT)
	                    .queryParam("offset", offset)
	                    .encode()
	                    .toUriString();
	
	            try {
	                ResponseEntity<Map> response = restTemplate.exchange(
	                        url,
	                        HttpMethod.GET,
	                        request,
	                        Map.class
	                );
	
	                Map<String, Object> body = response.getBody();
	
	                if (body == null) {
	                    continue;
	                }
	
	                Map<String, Object> tracks =
	                        (Map<String, Object>) body.get("tracks");
	
	                if (tracks == null) {
	                    continue;
	                }
	
	                List<Map<String, Object>> items =
	                        (List<Map<String, Object>>) tracks.get("items");
	
	                if (items == null || items.isEmpty()) {
	                    break;
	                }
	
	                for (Map<String, Object> track : items) {
	                    String id = Objects.toString(track.get("id"), "");
	
	                    if (id.isBlank()) {
	                        continue;
	                    }
	
	                    trackMap.putIfAbsent(id, track);
	
	                    if (trackMap.size() >= MAX_SEARCH_CANDIDATES) {
	                        break searchLoop;
	                    }
	                }
	
	                if (items.size() < SPOTIFY_SEARCH_LIMIT) {
	                    break;
	                }
	
	            } catch (HttpStatusCodeException e) {
	                printSpotifyHttpError("Spotify 검색 실패", e);
	
	                if (e.getStatusCode().value() == 429) {
	                    break searchLoop;
	                }
	            }
	        }
	    }
	
	    if (trackMap.isEmpty()) {
	        return List.of();
	    }
	
	    List<Map<String, Object>> sortedTracks = new ArrayList<>(trackMap.values());
	
	    sortedTracks.sort((a, b) -> {
	        int scoreA = calculateSearchScore(a, keyword);
	        int scoreB = calculateSearchScore(b, keyword);
	
	        return Integer.compare(scoreB, scoreA);
	    });
	
	    List<Map<String, Object>> result = new ArrayList<>();
	
	    for (Map<String, Object> track : sortedTracks) {
	        if (result.size() >= safeDisplayLimit) {
	            break;
	        }
	
	        result.add(convertTrackToCard(track, result.size() + 1));
	    }
	
	    return result;
	}

    // =========================
    // 검색어 후보 생성
    // 최대 4개만 사용해서 429 방지
    // =========================
    private List<String> createSearchKeywords(String keyword) {
    String trimmedKeyword = keyword == null ? "" : keyword.trim();

    if (trimmedKeyword.isEmpty()) {
        return List.of();
    }

    LinkedHashSet<String> keywords = new LinkedHashSet<>();

    List<String> knownTrackAliases = getKnownTrackAliases(trimmedKeyword);
    List<String> knownTrackArtistAliases = getKnownTrackArtistAliases(trimmedKeyword);
    List<String> artistAliases = getArtistAliases(trimmedKeyword);

    String normalizedKeyword = normalizeSearchText(trimmedKeyword);
    String englishKeyword = convertKoreanLoanwordToEnglish(trimmedKeyword);

    // 1순위: 사용자가 입력한 원문
    addSearchKeyword(keywords, trimmedKeyword);

    // 2순위: 알려진 곡명 + 아티스트 조합
    if (!knownTrackAliases.isEmpty()) {
        String koreanTrack = pickFirstAlias(knownTrackAliases);
        String englishTrack = pickEnglishAlias(knownTrackAliases);

        String koreanArtist = pickFirstAlias(knownTrackArtistAliases);
        String englishArtist = pickEnglishAlias(knownTrackArtistAliases);

        addSearchKeyword(keywords, koreanTrack + " " + koreanArtist);
        addSearchKeyword(keywords, englishTrack + " " + englishArtist);
        addSearchKeyword(keywords, koreanTrack);
        addSearchKeyword(keywords, englishTrack);
    }

    // 3순위: 아티스트명 검색
    if (!artistAliases.isEmpty()) {
        String koreanArtist = pickFirstAlias(artistAliases);
        String englishArtist = pickEnglishAlias(artistAliases);

        addSearchKeyword(keywords, koreanArtist);
        addSearchKeyword(keywords, englishArtist);
    }

    // 4순위: 정규화 / 영어 변환 검색어
    addSearchKeyword(keywords, normalizedKeyword);
    addSearchKeyword(keywords, englishKeyword);

    return keywords.stream()
            .filter(value -> value != null && !value.isBlank())
            .limit(MAX_SEARCH_KEYWORDS)
            .toList();
    }
    
    private void addSearchKeyword(Set<String> keywords, String value) {
        if (value == null) {
            return;
        }

        String trimmed = value.trim().replaceAll("\\s+", " ");

        if (trimmed.isBlank()) {
            return;
        }

        keywords.add(trimmed);
    }

    // =========================
    // 한글 아티스트 alias
    // =========================
    private List<String> getArtistAliases(String keyword) {
        String key = normalizeAliasKey(keyword);

        if (key.isEmpty()) {
            return List.of();
        }

        for (Map.Entry<String, List<String>> entry : getArtistAliasMap().entrySet()) {
            if (matchesAliasKey(key, normalizeAliasKey(entry.getKey()))) {
                return entry.getValue();
            }

            for (String alias : entry.getValue()) {
                if (matchesAliasKey(key, normalizeAliasKey(alias))) {
                    return entry.getValue();
                }
            }
        }

        return List.of();
    }

    private Map<String, List<String>> getArtistAliasMap() {
        Map<String, List<String>> aliasMap = new LinkedHashMap<>();

        aliasMap.put("박효신", List.of("박효신", "Park Hyo Shin"));
        aliasMap.put("허각", List.of("허각", "Huh Gak"));
        aliasMap.put("한로로", List.of("한로로", "Hanroro"));
        aliasMap.put("아이유", List.of("아이유", "IU"));
        aliasMap.put("방탄소년단", List.of("방탄소년단", "BTS"));
        aliasMap.put("르세라핌", List.of("르세라핌", "LE SSERAFIM"));
        aliasMap.put("아이브", List.of("아이브", "IVE"));
        aliasMap.put("뉴진스", List.of("뉴진스", "NewJeans"));
        aliasMap.put("에스파", List.of("에스파", "aespa"));

        return aliasMap;
    }

    // =========================
    // 한글 곡명 alias
    // =========================
    private List<String> getKnownTrackAliases(String keyword) {
        String canonicalKey = findCanonicalTrackKey(keyword);

        if (canonicalKey.isEmpty()) {
            return List.of();
        }

        return getTrackAliasMap().getOrDefault(canonicalKey, List.of());
    }

    private List<String> getKnownTrackArtistAliases(String keyword) {
        String canonicalKey = findCanonicalTrackKey(keyword);

        if (canonicalKey.isEmpty()) {
            return List.of();
        }

        if (canonicalKey.equals("눈의꽃")) {
            return getArtistAliases("박효신");
        }

        if (canonicalKey.equals("야생화")) {
            return getArtistAliases("박효신");
        }

        if (canonicalKey.equals("행복한나를")) {
            return getArtistAliases("허각");
        }

        if (canonicalKey.equals("러브다이브")) {
            return getArtistAliases("아이브");
        }

        return List.of();
    }

    private String findCanonicalTrackKey(String keyword) {
        String key = normalizeAliasKey(keyword);

        if (key.isEmpty()) {
            return "";
        }

        for (Map.Entry<String, List<String>> entry : getTrackAliasMap().entrySet()) {
            if (matchesAliasKey(key, normalizeAliasKey(entry.getKey()))) {
                return entry.getKey();
            }

            for (String alias : entry.getValue()) {
                if (matchesAliasKey(key, normalizeAliasKey(alias))) {
                    return entry.getKey();
                }
            }
        }

        return "";
    }

    private Map<String, List<String>> getTrackAliasMap() {
        Map<String, List<String>> aliasMap = new LinkedHashMap<>();

        aliasMap.put("눈의꽃", List.of("눈의 꽃", "눈의꽃", "Snow Flower"));
        aliasMap.put("야생화", List.of("야생화", "Wild Flower"));
        aliasMap.put("행복한나를", List.of("행복한 나를", "행복한나를", "Happy Me"));
        aliasMap.put("러브다이브", List.of("러브 다이브", "러브다이브", "LOVE DIVE", "Love Dive"));

        return aliasMap;
    }

    private boolean matchesAliasKey(String keywordKey, String aliasKey) {
        if (keywordKey == null || aliasKey == null || keywordKey.isBlank() || aliasKey.isBlank()) {
            return false;
        }

        if (keywordKey.equals(aliasKey)) {
            return true;
        }

        return aliasKey.length() >= 2 && keywordKey.contains(aliasKey);
    }

    private String pickFirstAlias(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return "";
        }

        return aliases.get(0);
    }

    private String pickEnglishAlias(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return "";
        }

        for (String alias : aliases) {
            if (alias != null && alias.matches(".*[a-zA-Z].*")) {
                return alias;
            }
        }

        return "";
    }

    // =========================
    // 한글 외래어 → 영어 검색어 변환
    // =========================
    private String convertKoreanLoanwordToEnglish(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String converted = normalizeSearchText(text);

        Map<String, String> dictionary = new LinkedHashMap<>();

        dictionary.put("러브", "love");
        dictionary.put("다이브", "dive");
        dictionary.put("아이엠", "i am");
        dictionary.put("아이", "i");
        dictionary.put("엠", "am");
        dictionary.put("애프터", "after");
        dictionary.put("라이크", "like");
        dictionary.put("일레븐", "eleven");
        dictionary.put("키치", "kitsch");
        dictionary.put("배디", "baddie");

        dictionary.put("슈퍼노바", "supernova");
        dictionary.put("드라마", "drama");
        dictionary.put("스파이시", "spicy");
        dictionary.put("아마겟돈", "armageddon");

        dictionary.put("하입", "hype");
        dictionary.put("보이", "boy");
        dictionary.put("디토", "ditto");
        dictionary.put("어텐션", "attention");
        dictionary.put("쿠키", "cookie");
        dictionary.put("슈퍼", "super");
        dictionary.put("샤이", "shy");
        dictionary.put("오엠지", "omg");

        dictionary.put("버터", "butter");
        dictionary.put("다이너마이트", "dynamite");
        dictionary.put("퍼미션", "permission");
        dictionary.put("댄스", "dance");

        dictionary.put("큐피드", "cupid");
        dictionary.put("마그네틱", "magnetic");
        dictionary.put("퍼펙트", "perfect");
        dictionary.put("나이트", "night");
        dictionary.put("이지", "easy");
        dictionary.put("스마트", "smart");
        dictionary.put("언포기븐", "unforgiven");
        dictionary.put("안티프래자일", "antifragile");
        dictionary.put("피어리스", "fearless");

        dictionary.put("눈의꽃", "snow flower");
        dictionary.put("눈의 꽃", "snow flower");
        dictionary.put("야생화", "wild flower");
        dictionary.put("행복한 나를", "happy me");
        dictionary.put("행복한나를", "happy me");

        for (Map.Entry<String, String> entry : dictionary.entrySet()) {
            converted = converted.replace(entry.getKey(), entry.getValue());
        }

        return converted.replaceAll("\\s+", " ").trim();
    }

    // =========================
    // 검색 점수 계산
    // =========================
    @SuppressWarnings("unchecked")
	private int calculateSearchScore(Map<String, Object> track, String keyword) {
	    String normalizedKeyword = normalizeSearchText(keyword);
	    String englishKeyword = convertKoreanLoanwordToEnglish(keyword);
	
	    String title = normalizeSearchText(
	            Objects.toString(track.get("name"), "")
	    );
	
	    String artistText = normalizeSearchText(getArtistText(track));
	    String albumName = normalizeSearchText(getAlbumName(track));
	
	    int score = 0;
	
	    List<String> titleAliases = getKnownTrackAliases(keyword);
	    List<String> artistAliases = getArtistAliases(keyword);
	    List<String> knownTrackArtistAliases = getKnownTrackArtistAliases(keyword);
	
	    boolean isKnownTrackSearch = !titleAliases.isEmpty();
	    boolean isArtistSearch = !artistAliases.isEmpty() && titleAliases.isEmpty();
	
	    // =========================
	    // 1. 알려진 곡명 alias 점수
	    // 예: 눈의꽃, 눈의 꽃, Snow Flower
	    // =========================
	    for (String titleAlias : titleAliases) {
	        String normalizedAlias = normalizeSearchText(titleAlias);
	
	        if (normalizedAlias.isBlank()) {
	            continue;
	        }
	
	        if (title.equals(normalizedAlias)) {
	            score += 6000;
	        } else if (title.contains(normalizedAlias)) {
	            score += 3500;
	        } else if (normalizedAlias.contains(title) && title.length() >= 2) {
	            score += 1200;
	        }
	
	        score += calculateWordOverlapScore(title, normalizedAlias, 250);
	    }
	
	    // =========================
	    // 2. 알려진 곡의 원곡/대표 아티스트 점수
	    // 예: 눈의 꽃 → 박효신
	    // =========================
	    for (String artistAlias : knownTrackArtistAliases) {
	        String normalizedArtistAlias = normalizeSearchText(artistAlias);
	
	        if (normalizedArtistAlias.isBlank()) {
	            continue;
	        }
	
	        if (artistText.equals(normalizedArtistAlias)) {
	            score += 4000;
	        } else if (artistText.contains(normalizedArtistAlias)) {
	            score += 2800;
	        }
	
	        score += calculateWordOverlapScore(artistText, normalizedArtistAlias, 220);
	    }
	
	    // 알려진 곡인데 대표 아티스트가 다르면 커버/동명이곡일 가능성이 높으니 감점
	    if (isKnownTrackSearch && !knownTrackArtistAliases.isEmpty()
	            && !containsAnyAlias(artistText, knownTrackArtistAliases)) {
	        score -= 2200;
	    }
	
	    // =========================
	    // 3. 아티스트 검색 점수
	    // 예: 박효신, Park Hyo Shin
	    // =========================
	    for (String artistAlias : artistAliases) {
	        String normalizedArtistAlias = normalizeSearchText(artistAlias);
	
	        if (normalizedArtistAlias.isBlank()) {
	            continue;
	        }
	
	        if (artistText.equals(normalizedArtistAlias)) {
	            score += 5000;
	        } else if (artistText.contains(normalizedArtistAlias)) {
	            score += 3500;
	        }
	
	        score += calculateWordOverlapScore(artistText, normalizedArtistAlias, 250);
	    }
	
	    // =========================
	    // 4. 일반 텍스트 매칭 점수
	    // =========================
	    score += calculateTextMatchScore(title, artistText, normalizedKeyword);
	
	    if (!englishKeyword.equals(normalizedKeyword)) {
	        score += calculateTextMatchScore(title, artistText, englishKeyword);
	    }
	
	    // =========================
	    // 5. Popularity 점수
	    // 곡명 정확 검색보다는 아티스트 검색일 때 popularity 영향 크게
	    // =========================
	    int popularityScore = getPopularityScore(track);
	
	    if (isKnownTrackSearch) {
	        score += popularityScore * 2;
	    } else if (isArtistSearch) {
	        score += popularityScore * 4;
	    } else {
	        score += popularityScore * 3;
	    }
	
	    // =========================
	    // 6. 원하지 않는 버전 감점
	    // 사용자가 remix/live/cover 등을 직접 검색하지 않았으면 아래 버전은 뒤로 보냄
	    // =========================
	    if (containsUnwantedVersionWord(title, normalizedKeyword)) {
	        score -= 1400;
	    }
	
	    if (containsUnwantedVersionWord(albumName, normalizedKeyword)) {
	        score -= 700;
	    }
	
	    return score;
	}

    private int calculateTextMatchScore(String title, String artistText, String keyword) {
    if (keyword == null || keyword.isBlank()) {
        return 0;
    }

    int score = 0;
    String fullText = (title + " " + artistText).trim();

    if (title.equals(keyword)) {
        score += 3000;
    }

    if (fullText.equals(keyword)) {
        score += 2500;
    }

    if (title.contains(keyword)) {
        score += 1600;
    }

    if (artistText.contains(keyword)) {
        score += 1300;
    }

    if (keyword.contains(title) && title.length() >= 2) {
        score += 800;
    }

    List<String> words = Arrays.stream(keyword.split("\\s+"))
            .filter(word -> !word.isBlank())
            .filter(word -> word.length() >= 2)
            .toList();

    if (words.isEmpty()) {
        return score;
    }

    int titleMatchCount = 0;
    int artistMatchCount = 0;

    for (String word : words) {
        if (title.equals(word)) {
            score += 500;
            titleMatchCount++;
        } else if (title.contains(word)) {
            score += 220;
            titleMatchCount++;
        }

        if (artistText.equals(word)) {
            score += 450;
            artistMatchCount++;
        } else if (artistText.contains(word)) {
            score += 180;
            artistMatchCount++;
        }
    }

    if (titleMatchCount == words.size()) {
        score += 900;
    }

    if (titleMatchCount + artistMatchCount >= words.size()) {
        score += 700;
    }

    if (words.size() >= 2) {
        String firstWord = words.get(0);
        String exceptFirst = String.join(" ", words.subList(1, words.size()));

        if (artistText.contains(firstWord) && title.contains(exceptFirst)) {
            score += 900;
        }

        String lastWord = words.get(words.size() - 1);
        String exceptLast = String.join(" ", words.subList(0, words.size() - 1));

        if (artistText.contains(lastWord) && title.contains(exceptLast)) {
            score += 900;
        }
    }

    return score;
    }
    
    @SuppressWarnings("unchecked")
    private String getArtistText(Map<String, Object> track) {
        List<Map<String, Object>> artists =
                (List<Map<String, Object>>) track.get("artists");

        if (artists == null || artists.isEmpty()) {
            return "";
        }

        StringBuilder artistBuilder = new StringBuilder();

        for (Map<String, Object> artist : artists) {
            artistBuilder.append(" ")
                    .append(Objects.toString(artist.get("name"), ""));
        }

        return artistBuilder.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String getAlbumName(Map<String, Object> track) {
        Map<String, Object> album =
                (Map<String, Object>) track.get("album");

        if (album == null) {
            return "";
        }

        return Objects.toString(album.get("name"), "");
    }

    private int getPopularityScore(Map<String, Object> track) {
        Number popularity = (Number) track.get("popularity");
        return popularity == null ? 0 : popularity.intValue();
    }

    private boolean containsAnyAlias(String text, List<String> aliases) {
        if (text == null || text.isBlank() || aliases == null || aliases.isEmpty()) {
            return false;
        }

        String normalizedText = normalizeSearchText(text);

        for (String alias : aliases) {
            String normalizedAlias = normalizeSearchText(alias);

            if (normalizedAlias.isBlank()) {
                continue;
            }

            if (normalizedText.equals(normalizedAlias)) {
                return true;
            }

            if (normalizedText.contains(normalizedAlias)) {
                return true;
            }

            if (calculateWordOverlapScore(normalizedText, normalizedAlias, 1) > 0) {
                return true;
            }
        }

        return false;
    }

    private int calculateWordOverlapScore(String target, String keyword, int pointPerWord) {
        if (target == null || keyword == null || target.isBlank() || keyword.isBlank()) {
            return 0;
        }

        List<String> words = Arrays.stream(keyword.split("\\s+"))
                .filter(word -> !word.isBlank())
                .filter(word -> word.length() >= 2)
                .toList();

        int score = 0;

        for (String word : words) {
            if (target.contains(word)) {
                score += pointPerWord;
            }
        }

        return score;
    }

    private boolean containsUnwantedVersionWord(String text, String keyword) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String safeKeyword = keyword == null ? "" : keyword;

        List<String> versionWords = List.of(
                "karaoke",
                "instrumental",
                "inst",
                "remix",
                "sped up",
                "slowed",
                "live",
                "cover",
                "version",
                "acoustic",
                "piano",
                "tribute"
        );

        for (String word : versionWords) {
            if (text.contains(word) && !safeKeyword.contains(word)) {
                return true;
            }
        }

        return false;
    }

	// =========================
	// Latest
	// 최신 앨범 기준으로 조회 후, 각 앨범의 첫 번째 트랙을 재생용 데이터로 변환
	// Spotify 요청 실패 시 stale cache → 실제 이미지 더미 데이터 순서로 반환
	// =========================
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getLatestReleases(int displayLimit) {
	    int safeDisplayLimit = Math.min(Math.max(displayLimit, 1), 100);
	    String cacheKey = "latest:" + safeDisplayLimit;
	
	    List<Map<String, Object>> cached = getCached(cacheKey);
	
	    if (cached != null) {
	        return cached;
	    }
	
	    List<Map<String, Object>> allAlbums = new ArrayList<>();
	
	    try {
	        String accessToken = getAccessToken();
	
	        HttpHeaders headers = new HttpHeaders();
	        headers.setBearerAuth(accessToken);
	
	        HttpEntity<Void> request = new HttpEntity<>(headers);
	
	        int spotifyLimit = 10;
	
	        for (int offset = 0; offset < safeDisplayLimit; offset += spotifyLimit) {
	            int requestLimit = Math.min(spotifyLimit, safeDisplayLimit - offset);
	
	            String url = UriComponentsBuilder
	                    .fromUriString("https://api.spotify.com/v1/search")
	                    .queryParam("q", "tag:new")
	                    .queryParam("type", "album")
	                    .queryParam("market", "KR")
	                    .queryParam("limit", requestLimit)
	                    .queryParam("offset", offset)
	                    .encode()
	                    .toUriString();
	
	            ResponseEntity<Map> response = restTemplate.exchange(
	                    url,
	                    HttpMethod.GET,
	                    request,
	                    Map.class
	            );
	
	            Map<String, Object> body = response.getBody();
	
	            if (body == null) {
	                break;
	            }
	
	            Map<String, Object> albums =
	                    (Map<String, Object>) body.get("albums");
	
	            if (albums == null) {
	                break;
	            }
	
	            List<Map<String, Object>> items =
	                    (List<Map<String, Object>>) albums.get("items");
	
	            if (items == null || items.isEmpty()) {
	                break;
	            }
	
	            allAlbums.addAll(items);
	
	            if (items.size() < requestLimit) {
	                break;
	            }
	        }
	
	        if (allAlbums.isEmpty()) {
	            return getLatestDummyReleases(safeDisplayLimit);
	        }
	
	        allAlbums.sort((a, b) -> {
	            String dateA = Objects.toString(a.get("release_date"), "");
	            String dateB = Objects.toString(b.get("release_date"), "");
	
	            return dateB.compareTo(dateA);
	        });
	
	        List<Map<String, Object>> result = new ArrayList<>();
	
	        for (Map<String, Object> album : allAlbums) {
	            if (result.size() >= safeDisplayLimit) {
	                break;
	            }
	
	            String albumId = Objects.toString(album.get("id"), "");
	
	            if (albumId.isBlank()) {
	                continue;
	            }
	
	            Map<String, Object> firstTrack = getFirstTrackFromAlbum(albumId, accessToken);
	
	            if (firstTrack == null || firstTrack.isEmpty()) {
	                continue;
	            }
	
	            result.add(convertAlbumAndTrackToCard(album, firstTrack, result.size() + 1));
	        }
	
	        if (result.isEmpty()) {
	            return getLatestDummyReleases(safeDisplayLimit);
	        }
	
	        putCache(cacheKey, result, 10 * 60 * 1000L);
	
	        return result;
	
	    } catch (HttpStatusCodeException e) {
	        printSpotifyHttpError("Spotify 최신 앨범/트랙 검색 실패", e);
	
	        List<Map<String, Object>> stale = getStaleCache(cacheKey);
	
	        if (stale != null) {
	            return stale;
	        }
	
	        return getLatestDummyReleases(safeDisplayLimit);
	
	    } catch (Exception e) {
	        e.printStackTrace();
	
	        List<Map<String, Object>> stale = getStaleCache(cacheKey);
	
	        if (stale != null) {
	            return stale;
	        }
	
	        return getLatestDummyReleases(safeDisplayLimit);
	    }
	}
	
	// =========================
	// Latest Dummy Data
	// Spotify API 실패 시 화면이 비지 않도록 실제 Spotify 이미지 / id / uri 데이터 반환
	// =========================
	private List<Map<String, Object>> getLatestDummyReleases(int displayLimit) {
	    List<Map<String, Object>> dummyAlbums = List.of(
	            createDummyAlbum(
	                    "dummy-album-1",
	                    "404 (New Era)",
	                    "KiiiKiii",
	                    "2026-06-01",
	                    "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e028c77cd2419fcdb44198262a3"
	            ),
	            createDummyAlbum(
	                    "dummy-album-2",
	                    "Way Back Home",
	                    "SHAUN",
	                    "2026-05-28",
	                    "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e029bb453695e0776ceb13576f3"
	            ),
	            createDummyAlbum(
	                    "dummy-album-3",
	                    "Cosmic",
	                    "Red Velvet",
	                    "2026-05-25",
	                    "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e0233f4f800b259791768d04f40"
	            ),
	            createDummyAlbum(
	                    "dummy-album-4",
	                    "toxic till the end",
	                    "ROSÉ",
	                    "2026-05-20",
	                    "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e02a9fb6e00986e42ad4764b1f3"
	            ),
	            createDummyAlbum(
	                    "dummy-album-5",
	                    "사랑하게 될 거야",
	                    "한로로",
	                    "2026-05-15",
	                    "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e02041c2df6c6417db26f4133d4"
	            ),
	            createDummyAlbum(
	                    "dummy-album-6",
	                    "타임캡슐",
	                    "다비치",
	                    "2026-05-10",
	                    "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e0242f191e863eb9f2c9325a6e6"
	            )
	    );
	
	    List<Map<String, Object>> dummyTracks = List.of(
	            createDummyTrack(
	                    "24rDDbSlFY9OHrlJb48CRh",
	                    "spotify:track:24rDDbSlFY9OHrlJb48CRh",
	                    "404 (New Era)",
	                    "KiiiKiii"
	            ),
	            createDummyTrack(
	                    "3NxuezMdSLgt4OwHzBoUhL",
	                    "spotify:track:3NxuezMdSLgt4OwHzBoUhL",
	                    "Way Back Home",
	                    "SHAUN"
	            ),
	            createDummyTrack(
	                    "49ciDis1ofgszcKXKh0Sqb",
	                    "spotify:track:49ciDis1ofgszcKXKh0Sqb",
	                    "Cosmic",
	                    "Red Velvet"
	            ),
	            createDummyTrack(
	                    "1z5ebC9238uGoBgzYyvGpQ",
	                    "spotify:track:1z5ebC9238uGoBgzYyvGpQ",
	                    "toxic till the end",
	                    "ROSÉ"
	            ),
	            createDummyTrack(
	                    "3WvM2dIR9iIxMGNMP7WsNw",
	                    "spotify:track:3WvM2dIR9iIxMGNMP7WsNw",
	                    "사랑하게 될 거야",
	                    "한로로"
	            ),
	            createDummyTrack(
	                    "3CQw6HqsBu12wUj89vUQ5M",
	                    "spotify:track:3CQw6HqsBu12wUj89vUQ5M",
	                    "타임캡슐",
	                    "다비치"
	            )
	    );
	
	    List<Map<String, Object>> result = new ArrayList<>();
	
	    for (int i = 0; i < dummyAlbums.size(); i++) {
	        if (result.size() >= displayLimit) {
	            break;
	        }
	
	        Map<String, Object> album = dummyAlbums.get(i);
	        Map<String, Object> track = dummyTracks.get(i);
	
	        result.add(convertAlbumAndTrackToCard(album, track, result.size() + 1));
	    }
	
	    return result;
	}

	private Map<String, Object> createDummyTrack(
	        String id,
	        String uri,
	        String name,
	        String artistName
	) {
	    return Map.of(
	            "id", id,
	            "uri", uri,
	            "name", name,
	            "artists", List.of(
	                    Map.of("name", artistName)
	            ),
	            "preview_url", "",
	            "duration_ms", 180000,
	            "external_urls", Map.of(
	                    "spotify", "https://open.spotify.com/track/" + id
	            )
	    );
	}

	private Map<String, Object> createDummyAlbum(
	        String id,
	        String name,
	        String artistName,
	        String releaseDate,
	        String imageUrl
	) {
	    return Map.of(
	            "id", id,
	            "name", name,
	            "release_date", releaseDate,
	            "artists", List.of(
	                    Map.of("name", artistName)
	            ),
	            "images", List.of(
	                    Map.of("url", imageUrl)
	            ),
	            "external_urls", Map.of(
	                    "spotify", ""
	            )
	    );
	}
	
	// =========================
	// 앨범의 첫 번째 트랙 조회 함수
	// =========================
	@SuppressWarnings("unchecked")
	private Map<String, Object> getFirstTrackFromAlbum(String albumId, String accessToken) {
	    String url = UriComponentsBuilder
	            .fromUriString("https://api.spotify.com/v1/albums/" + albumId + "/tracks")
	            .queryParam("market", "KR")
	            .queryParam("limit", 1)
	            .encode()
	            .toUriString();

	    HttpHeaders headers = new HttpHeaders();
	    headers.setBearerAuth(accessToken);

	    HttpEntity<Void> request = new HttpEntity<>(headers);

	    try {
	        ResponseEntity<Map> response = restTemplate.exchange(
	                url,
	                HttpMethod.GET,
	                request,
	                Map.class
	        );

	        Map<String, Object> body = response.getBody();

	        if (body == null) {
	            return null;
	        }

	        List<Map<String, Object>> items =
	                (List<Map<String, Object>>) body.get("items");

	        if (items == null || items.isEmpty()) {
	            return null;
	        }

	        return items.get(0);
	    } catch (HttpStatusCodeException e) {
	        printSpotifyHttpError("Spotify 앨범 트랙 조회 실패", e);
	        return null;
	    }
	}

	// =========================
	// 앨범 + 트랙 데이터를 프론트 카드 데이터로 변환
	// =========================
	@SuppressWarnings("unchecked")
	private Map<String, Object> convertAlbumAndTrackToCard(
	        Map<String, Object> album,
	        Map<String, Object> track,
	        int rank
	) {
	    String id = Objects.toString(track.get("id"), "");
	    String uri = Objects.toString(track.get("uri"), "");
	    String title = Objects.toString(track.get("name"), "Unknown Title");

	    Number durationMsValue = (Number) track.get("duration_ms");
	    int durationMs = durationMsValue == null ? 0 : durationMsValue.intValue();

	    String albumName = Objects.toString(album.get("name"), "");
	    String releaseDate = Objects.toString(album.get("release_date"), "");

	    List<Map<String, Object>> artists =
	            (List<Map<String, Object>>) album.get("artists");

	    String artistName = "Unknown Artist";

	    if (artists != null && !artists.isEmpty()) {
	        artistName = Objects.toString(artists.get(0).get("name"), "Unknown Artist");
	    }

	    String cover = "";

	    List<Map<String, Object>> images =
	            (List<Map<String, Object>>) album.get("images");

	    if (images != null && !images.isEmpty()) {
	        cover = Objects.toString(images.get(0).get("url"), "");
	    }

	    Map<String, Object> card = new LinkedHashMap<>();
	    card.put("id", id);
	    card.put("uri", uri);
	    card.put("rank", rank);
	    card.put("title", title);
	    card.put("description", artistName);
	    card.put("artist", artistName);
	    card.put("albumName", albumName);
	    card.put("cover", cover);
	    card.put("releaseDate", releaseDate);
	    card.put("durationMs", durationMs);

	    return card;
	}
	 

    @SuppressWarnings("unchecked")
	private Map<String, Object> convertTrackToCard(Map<String, Object> track, int rank) {
	    String id = Objects.toString(track.get("id"), "");
	    String uri = Objects.toString(track.get("uri"), "");
	    String title = Objects.toString(track.get("name"), "Unknown Title");
	
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
	
	    Number popularity = (Number) track.get("popularity");
	    int popularityScore = popularity == null ? 0 : popularity.intValue();
	
	    Map<String, Object> card = new LinkedHashMap<>();
	    card.put("id", id);
	    card.put("uri", uri);
	    card.put("rank", rank);
	    card.put("title", title);
	    card.put("description", artistName);
	    card.put("artist", artistName);
	    card.put("albumName", albumName);
	    card.put("cover", cover);
	    card.put("releaseDate", releaseDate);
	    card.put("durationMs", durationMs);
	    card.put("popularity", popularityScore);
	    card.put("weather", getWeatherByTrackId(id));
	
	    return card;
	}

    // =========================
    // 검색어 정규화
    // =========================
    private String normalizeSearchText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeAliasKey(String text) {
        return normalizeSearchText(text)
                .replaceAll("\\s+", "")
                .trim();
    }

    // =========================
    // 단순 메모리 캐시
    // =========================
    private synchronized List<Map<String, Object>> getCached(String key) {
        CachedList cachedList = cache.get(key);

        if (cachedList == null) {
            return null;
        }

        long now = System.currentTimeMillis();

        if (now > cachedList.expiredAt) {
            return null;
        }

        return cachedList.data;
    }

    private synchronized List<Map<String, Object>> getStaleCache(String key) {
        CachedList cachedList = cache.get(key);

        if (cachedList == null) {
            return null;
        }

        return cachedList.data;
    }

    private synchronized void putCache(
            String key,
            List<Map<String, Object>> data,
            long durationMillis
    ) {
        if (key == null || key.isBlank()) {
            return;
        }

        if (data == null || data.isEmpty()) {
            return;
        }

        cache.put(
                key,
                new CachedList(
                        data,
                        System.currentTimeMillis() + durationMillis
                )
        );
    }

    // =========================
    // Spotify HTTP 에러 로그 출력
    // =========================
    private void printSpotifyHttpError(String label, HttpStatusCodeException e) {
        System.out.println(label + " 상태 코드: " + e.getStatusCode());
        System.out.println(label + " Retry-After: " + e.getResponseHeaders().getFirst("Retry-After"));
        System.out.println(label + " 응답: " + e.getResponseBodyAsString());
    }

    private static class CachedList {
        private final List<Map<String, Object>> data;
        private final long expiredAt;

        private CachedList(List<Map<String, Object>> data, long expiredAt) {
            this.data = data;
            this.expiredAt = expiredAt;
        }
    }
}