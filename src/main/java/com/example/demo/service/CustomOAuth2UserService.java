package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.example.demo.mapper.UsersMapper;
import com.example.demo.vo.UsersVO;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UsersMapper usersMapper;

    // [동작 흐름 1] 사용자가 스포티파이 로그인에 성공하면 이 메서드가 자동 실행됨
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        // 부모 클래스의 loadUser를 호출해 스포티파이로부터 유저 정보를 가져옴
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // [동작 흐름 2] 유저 정보 추출 (Map 형태)
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String spotifyId = (String) attributes.get("id");
        String name = (String) attributes.get("display_name");
        String email = (String) attributes.get("email");

        // [동작 흐름 3] DB 처리 로직 실행
        saveOrUpdate(spotifyId, name, email);

        return oAuth2User;
    }

    private void saveOrUpdate(String spotifyId, String name, String email) {
        // [동작 흐름 4] 기존에 가입된 회원인지 Mapper를 통해 확인
        UsersVO existingMember = usersMapper.selectMemberBySpotifyId(spotifyId);

        if (existingMember == null) {
            // [동작 흐름 5-A] 없다면? 신규 가입 (Insert)
            UsersVO newMember = new UsersVO();
            newMember.setSpotifyId(spotifyId);
            newMember.setUserName(name);
            newMember.setUserMail(email);
            usersMapper.insertMember(newMember);
            System.out.println("DB 저장 완료!");
        } else {
            // [동작 흐름 5-B] 있다면? 닉네임이나 이메일이 바뀌었을 수 있으니 갱신 (Update)
            existingMember.setUserName(name);
            existingMember.setUserMail(email);
            usersMapper.updateMember(existingMember);
        }
    }
}