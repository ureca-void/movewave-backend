package com.example.demo.mapper;


import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.example.demo.vo.UsersVO;

@Mapper
public interface UsersMapper {
	
	// 1. 가입된 회원인지 확인하기 위해 Spotify ID로 조회
	@Select("SELECT * FROM users WHERE spotify_id = #{spotifyId}")
    UsersVO selectMemberBySpotifyId(String spotifyId);

    // 2. 처음 로그인한 사용자라면 DB에 인서트
	@Insert("INSERT INTO users (user_name, user_mail, spotify_id) VALUES (#{userName}, #{userMail}, #{spotifyId})")
    int insertMember(UsersVO vo);

    // 3. 이미 가입된 사용자라면 정보를 최신으로 업데이트
	@Update("UPDATE users SET user_name = #{userName}, user_mail = #{userMail} WHERE spotify_id = #{spotifyId}")
	@Options(useGeneratedKeys=true,keyProperty="id")
    int updateMember(UsersVO vo);

}
