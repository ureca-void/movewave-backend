package com.example.demo.controller;

import com.example.demo.service.LikeService;
import com.example.demo.vo.LikeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UsersController {

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    @Autowired
    public LikeService likeService;

    public UsersController(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    // =========================
    // 좋아요 목록 조회
    // =========================
    @GetMapping("/like")
    public ResponseEntity<?> showLikes(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String spotifyId = oAuth2User.getAttribute("id");

        return ResponseEntity.ok(likeService.getLike(spotifyId));
    }

    // =========================
    // 좋아요 추가 / 취소 토글
    // =========================
    @PostMapping("/like")
    public Map<String, Object> toggleLike(
            @RequestBody LikeVO likeVO,
            Authentication authentication
    ) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return Map.of("result", "fail", "message", "로그인이 필요합니다.");
        }

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String spotifyId = oAuth2User.getAttribute("id");

        likeVO.setSpotifyId(spotifyId);

        if (likeService.add(likeVO)) {
            return Map.of("result", "ok", "message", "좋아요 저장");
        }

        if (likeService.remove(likeVO.getSpotifyId(), likeVO.getMusicId())) {
            return Map.of("result", "ok", "message", "좋아요 취소");
        }

        return Map.of("result", "fail", "message", "서버 오류");
    }

    // =========================
    // 좋아요 삭제
    // =========================
    @DeleteMapping("/like/{musicId}")
    public Map<String, Object> deleteLike(
            @PathVariable("musicId") String musicId,
            Authentication authentication
    ) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return Map.of("result", "fail", "message", "로그인이 필요합니다.");
        }

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String spotifyId = oAuth2User.getAttribute("id");

        if (likeService.remove(spotifyId, musicId)) {
            return Map.of("result", "ok", "message", "좋아요 제거");
        }

        return Map.of("result", "fail", "message", "제거 실패");
    }

    // =========================
    // 현재 곡 좋아요 여부 확인
    // =========================
    @PostMapping("/islike")
    public Map<String, Object> showHeart(
            @RequestBody LikeVO likeVO,
            Authentication authentication
    ) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return Map.of("result", "fail", "message", "로그인이 필요합니다.");
        }

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String spotifyId = oAuth2User.getAttribute("id");

        likeVO.setSpotifyId(spotifyId);

        if (likeService.exists(likeVO)) {
            return Map.of("result", "ok", "message", "좋아요 함");
        }

        return Map.of("result", "fail", "message", "좋아요 안함");
    }

    // =========================
    // 유저 정보 조회
    // =========================
    @GetMapping("/user")
    public Map<String, Object> getUserInfo(Authentication authentication) {
        Map<String, Object> userInfo = new HashMap<>();

        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2User oAuth2User = oauthToken.getPrincipal();

            userInfo.put("spotifyId", oAuth2User.getAttribute("id"));
            userInfo.put("name", oAuth2User.getAttribute("display_name"));
            userInfo.put("email", oAuth2User.getAttribute("email"));

            List<Map<String, Object>> images =
                    (List<Map<String, Object>>) oAuth2User.getAttribute("images");

            String imageUrl = null;

            if (images != null && !images.isEmpty()) {
                imageUrl = (String) images.get(0).get("url");
            }

            userInfo.put("avatar", imageUrl);
            userInfo.put("isLoggedIn", true);

            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId(oauthToken.getAuthorizedClientRegistrationId())
                    .principal(authentication)
                    .build();

            OAuth2AuthorizedClient client = authorizedClientManager.authorize(authorizeRequest);

            if (client != null) {
                String accessToken = client.getAccessToken().getTokenValue();
                userInfo.put("accessToken", accessToken);
            }
        } else {
            userInfo.put("isLoggedIn", false);
        }

        return userInfo;
    }
}