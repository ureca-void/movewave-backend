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
// @CrossOrigin은 SecurityConfig에서 설정했다면 생략 가능하지만, 안전을 위해 두셔도 됩니다.
public class UsersController {

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    // 생성자 주입
    public UsersController(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    @Autowired
    public LikeService likeService;

    @GetMapping("/like")
    public ResponseEntity<?> showLikes(Authentication authentication) {
        // 1. 로그인 확인
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "로그인이 필요합니다."));
        }

        // 2. 유저 정보 추출
        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String spotifyId = oAuth2User.getAttribute("id");

        // 3. 데이터 조회 및 반환
        return ResponseEntity.ok(likeService.getLike(spotifyId));
    }

    @PostMapping("/like")
    public Map<String, Object> toggleLike(@RequestBody LikeVO likeVO, Authentication authentication){
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return Map.of("message", "로그인이 필요합니다.");
        }

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String spotifyId = oAuth2User.getAttribute("id");

        // 2. 프론트에서 넘어온 likeVO에 서버에서 얻은 진짜 spotifyId를 주입
        likeVO.setSpotifyId(spotifyId);
        // 3. 서비스 로직 수행
        if (likeService.add(likeVO)) {
            return Map.of("message", "좋아요 저장");
        } else {
            // 이미 존재해서 제거하는 경우
            if (likeService.remove(likeVO.getSpotifyId(), likeVO.getMusicId())) {
                return Map.of("message", "좋아요 취소");
            }
        }

        return Map.of("message", "서버 오류");
    }
    @PostMapping("/islike")
    public Map<String, Object> showHeart(@RequestBody LikeVO likeVO, Authentication authentication){
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return Map.of("result","fail","message", "로그인이 필요합니다.");
        }

        OAuth2User oAuth2User = oauthToken.getPrincipal();
        String spotifyId = oAuth2User.getAttribute("id");

        // 2. 프론트에서 넘어온 likeVO에 서버에서 얻은 진짜 spotifyId를 주입
        likeVO.setSpotifyId(spotifyId);
        if(likeService.exists(likeVO)){
            return Map.of("result","ok","message","좋아요 함");
        }
        return Map.of("result","fail","message","좋아요 안함");
    }

    @GetMapping("/user") // 상위 Mapping이 /api 이므로 실제 주소는 /api/user가 됩니다.
    public Map<String, Object> getUserInfo(Authentication authentication) {
        Map<String, Object> userInfo = new HashMap<>();

        // 1. 로그인 여부 확인
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            // 2. 유저 정보 추출 (이름, ID 등)
            OAuth2User oAuth2User = oauthToken.getPrincipal();
            userInfo.put("spotifyId", oAuth2User.getAttribute("id"));
            userInfo.put("name", oAuth2User.getAttribute("display_name"));
            userInfo.put("email", oAuth2User.getAttribute("email"));
            List<Map<String, Object>> images = (List<Map<String, Object>>) oAuth2User.getAttribute("images");
            String imageUrl = null;
            if (images != null && !images.isEmpty()) {
                // 보통 첫 번째 이미지(index 0)가 가장 큰 해상도입니다.
                imageUrl = (String) images.get(0).get("url");
            }
            userInfo.put("avatar",imageUrl);
            userInfo.put("isLoggedIn", true);

            // 3. 만료 시 자동 갱신되는 AccessToken 가져오기 ⭐
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
            // 로그인 안 된 경우
            userInfo.put("isLoggedIn", false);
        }

        return userInfo;
    }
}