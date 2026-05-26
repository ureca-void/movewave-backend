package com.example.demo.controller;



import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.vo.UsersVO;

@RestController
@RequestMapping("/")
@CrossOrigin(origins="*")
public class MainController {
		
	@GetMapping("/home")
    public String home(@AuthenticationPrincipal OAuth2User user, Model model) {
        // [동작 흐름 6] 로그인이 완료되면 세션에 저장된 유저 정보를 @AuthenticationPrincipal로 꺼낼 수 있음
        if (user != null) {
            model.addAttribute("name", user.getAttribute("display_name"));
        }
        return "home";
    }
	
}
