package com.example.demo.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UsersController {
    @GetMapping
    public Map<String, Object> getUserInfo(@AuthenticationPrincipal OAuth2User user) {
        Map<String, Object> userInfo = new HashMap<>();

        if (user != null) {
            // 스포티파이에서 받아온 정보 중 필요한 것만 골라 담습니다.
            userInfo.put("name", user.getAttribute("display_name"));
            userInfo.put("email", user.getAttribute("email"));
            userInfo.put("isLoggedIn", true);
        } else {
            userInfo.put("isLoggedIn", false);
        }

        return userInfo; // 객체를 반환하면 스프링이 알아서 JSON { "name": "...", ... } 형식으로 바꿔줍니다.
    }
}
