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
    // 고정 데이터
    // =========================
    public List<Map<String, Object>> getUrecaPicks() {
        return List.of(
                Map.of(
                        "id", "u1",
                        "title", "404 (New Era)",
                        "description", "KiiiKiii",
                        "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e028c77cd2419fcdb44198262a3"
                ),
                Map.of(
                        "id", "u2",
                        "title", "Way Back Home",
                        "description", "SHAUN",
                        "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e029bb453695e0776ceb13576f3"
                ),
                Map.of(
                        "id", "u3",
                        "title", "Cosmic",
                        "description", "Red Velvet",
                        "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e0233f4f800b259791768d04f40"
                ),
                Map.of(
                        "id", "u4",
                        "title", "toxic till the end",
                        "description", "ROSÉ",
                        "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e02a9fb6e00986e42ad4764b1f3"
                ),
                Map.of(
                        "id", "u5",
                        "title", "사랑하게 될 거야",
                        "description", "한로로",
                        "cover", "https://image-cdn-ak.spotifycdn.com/image/ab67616d00001e02041c2df6c6417db26f4133d4"
                ),
                Map.of(
                        "id", "u6",
                        "title", "타임캡슐",
                        "description", "다비치",
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

    public List<Map<String, Object>> getPopularKpopTracks(int displayLimit) {
        return getPopularTracks(displayLimit);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchTracks1(String keyword, int searchLimit, int displayLimit) {
        String accessToken = getAccessToken();

        int safeDisplayLimit = Math.min(Math.max(displayLimit, 1), 10);
        int safeSearchLimit = Math.min(Math.max(searchLimit, safeDisplayLimit), 10);

        String url = UriComponentsBuilder
                .fromUriString("https://api.spotify.com/v1/search")
                .queryParam("q", keyword)
                .queryParam("type", "track")
                .queryParam("market", "KR")
                .queryParam("limit", safeSearchLimit)
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
                throw new RuntimeException("Spotify 검색 응답이 비어있습니다.");
            }

            Map<String, Object> tracks =
                    (Map<String, Object>) body.get("tracks");

            if (tracks == null) {
                throw new RuntimeException("Spotify tracks 데이터가 없습니다.");
            }

            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) tracks.get("items");

            if (items == null || items.isEmpty()) {
                return List.of();
            }

            items.sort((a, b) -> {
                Number popularityA = (Number) a.get("popularity");
                Number popularityB = (Number) b.get("popularity");

                int scoreA = popularityA == null ? 0 : popularityA.intValue();
                int scoreB = popularityB == null ? 0 : popularityB.intValue();

                return Integer.compare(scoreB, scoreA);
            });

            List<Map<String, Object>> result = new ArrayList<>();

            for (Map<String, Object> track : items) {
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
    private List<Map<String, Object>> searchTracksByKeywordFallback(
            String keyword,
            int safeDisplayLimit,
            String accessToken
    ) {
        int pageLimit = Math.min(Math.max(safeDisplayLimit, 1), 10);

        List<String> searchKeywords = createSearchKeywords(keyword);

        if (searchKeywords.isEmpty()) {
            return List.of();
        }

        Map<String, Map<String, Object>> trackMap = new LinkedHashMap<>();

        for (String searchKeyword : searchKeywords) {
            if (trackMap.size() >= safeDisplayLimit) {
                break;
            }

            String url = UriComponentsBuilder
                    .fromUriString("https://api.spotify.com/v1/search")
                    .queryParam("q", searchKeyword)
                    .queryParam("type", "track")
                    .queryParam("market", "KR")
                    .queryParam("limit", pageLimit)
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
                    continue;
                }

                for (Map<String, Object> track : items) {
                    String id = Objects.toString(track.get("id"), "");

                    if (id.isEmpty()) {
                        continue;
                    }

                    trackMap.putIfAbsent(id, track);

                    if (trackMap.size() >= safeDisplayLimit) {
                        break;
                    }
                }

            } catch (HttpStatusCodeException e) {
                printSpotifyHttpError("Spotify 검색 실패", e);

                if (e.getStatusCode().value() == 429) {
                    break;
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

        if (!knownTrackAliases.isEmpty()) {
            String koreanTrack = pickFirstAlias(knownTrackAliases);
            String englishTrack = pickEnglishAlias(knownTrackAliases);
            String koreanArtist = pickFirstAlias(knownTrackArtistAliases);
            String englishArtist = pickEnglishAlias(knownTrackArtistAliases);

            if (!koreanTrack.isEmpty() && !koreanArtist.isEmpty()) {
                keywords.add(koreanTrack + " " + koreanArtist);
            }

            if (!englishTrack.isEmpty() && !englishArtist.isEmpty()) {
                keywords.add(englishTrack + " " + englishArtist);
            }

            if (!koreanTrack.isEmpty()) {
                keywords.add(koreanTrack);
            }

            if (!englishTrack.isEmpty()) {
                keywords.add(englishTrack);
            }
        }

        if (!artistAliases.isEmpty()) {
            String koreanArtist = pickFirstAlias(artistAliases);
            String englishArtist = pickEnglishAlias(artistAliases);

            if (!koreanArtist.isEmpty()) {
                keywords.add(koreanArtist);
            }

            if (!englishArtist.isEmpty()) {
                keywords.add(englishArtist);
            }

            if (!englishArtist.isEmpty()) {
                keywords.add("artist:\"" + englishArtist + "\"");
            }
        }

        keywords.add(trimmedKeyword);

        String normalizedKeyword = normalizeSearchText(trimmedKeyword);

        if (!normalizedKeyword.isEmpty()) {
            keywords.add(normalizedKeyword);
        }

        String englishKeyword = convertKoreanLoanwordToEnglish(trimmedKeyword);

        if (!englishKeyword.isEmpty()) {
            keywords.add(englishKeyword);
        }

        return keywords.stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(4)
                .toList();
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

        int score = 0;

        List<String> titleAliases = getKnownTrackAliases(keyword);
        List<String> artistAliases = getArtistAliases(keyword);
        List<String> knownTrackArtistAliases = getKnownTrackArtistAliases(keyword);

        for (String titleAlias : titleAliases) {
            String normalizedAlias = normalizeSearchText(titleAlias);

            if (title.equals(normalizedAlias)) {
                score += 2500;
            }

            if (title.contains(normalizedAlias)) {
                score += 1200;
            }

            if (normalizedAlias.contains(title) && title.length() >= 2) {
                score += 700;
            }
        }

        for (String artistAlias : knownTrackArtistAliases) {
            String normalizedArtistAlias = normalizeSearchText(artistAlias);

            if (artistText.equals(normalizedArtistAlias)) {
                score += 1500;
            }

            if (artistText.contains(normalizedArtistAlias)) {
                score += 1000;
            }
        }

        for (String artistAlias : artistAliases) {
            String normalizedArtistAlias = normalizeSearchText(artistAlias);

            if (artistText.equals(normalizedArtistAlias)) {
                score += 1500;
            }

            if (artistText.contains(normalizedArtistAlias)) {
                score += 1000;
            }
        }

        score += calculateTextMatchScore(title, artistText, normalizedKeyword);
        score += calculateTextMatchScore(title, artistText, englishKeyword);

        Number popularity = (Number) track.get("popularity");
        int popularityScore = popularity == null ? 0 : popularity.intValue();

        score += Math.min(popularityScore, 100);

        return score;
    }

    private int calculateTextMatchScore(String title, String artistText, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return 0;
        }

        int score = 0;

        if (title.equals(keyword)) {
            score += 500;
        }

        if (title.contains(keyword)) {
            score += 300;
        }

        if (keyword.contains(title) && title.length() >= 2) {
            score += 150;
        }

        List<String> words = Arrays.stream(keyword.split("\\s+"))
                .filter(word -> !word.isBlank())
                .filter(word -> word.length() >= 2)
                .toList();

        for (String word : words) {
            if (title.contains(word)) {
                score += 80;
            }

            if (artistText.contains(word)) {
                score += 70;
            }
        }

        if (words.size() >= 2) {
            String firstWord = words.get(0);
            String exceptFirst = String.join(" ", words.subList(1, words.size()));

            if (artistText.contains(firstWord) && title.contains(exceptFirst)) {
                score += 350;
            }

            String lastWord = words.get(words.size() - 1);
            String exceptLast = String.join(" ", words.subList(0, words.size() - 1));

            if (artistText.contains(lastWord) && title.contains(exceptLast)) {
                score += 350;
            }
        }

        return score;
    }

	// =========================
	// Latest
	// 최신 앨범 기준으로 조회 후, 각 앨범의 첫 번째 트랙을 재생용 데이터로 변환
	// =========================
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getLatestReleases(int displayLimit) {
	    int safeDisplayLimit = Math.min(Math.max(displayLimit, 1), 100);
	    String cacheKey = "latest:" + safeDisplayLimit;
	
	    List<Map<String, Object>> cached = getCached(cacheKey);
	
	    if (cached != null) {
	        return cached;
	    }
	
	    String accessToken = getAccessToken();
	
	    HttpHeaders headers = new HttpHeaders();
	    headers.setBearerAuth(accessToken);
	
	    HttpEntity<Void> request = new HttpEntity<>(headers);
	
	    List<Map<String, Object>> allAlbums = new ArrayList<>();
	
	    try {
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
	            return List.of();
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
	
	        putCache(cacheKey, result, 10 * 60 * 1000L);
	
	        return result;
	
	    } catch (HttpStatusCodeException e) {
	        printSpotifyHttpError("Spotify 최신 앨범/트랙 검색 실패", e);
	
	        List<Map<String, Object>> stale = getStaleCache(cacheKey);
	
	        if (stale != null) {
	            return stale;
	        }
	
	        throw new RuntimeException("Spotify 최신 앨범/트랙 검색 실패", e);
	    }
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
	    String artistId = "";

	    if (artists != null && !artists.isEmpty()) {
	        artistName = Objects.toString(artists.get(0).get("name"), "Unknown Artist");
	        artistId = Objects.toString(artists.get(0).get("id"), "");
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