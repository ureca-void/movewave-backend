package com.example.demo.mapper;

import com.example.demo.vo.LikeVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LikeMapper {
    // 1. 가입된 회원인지 확인하기 위해 Spotify ID로 조회
    @Select("SELECT * FROM liked WHERE spotify_id = #{spotifyId}")
    List<LikeVO> findLike(String spotifyId);

    @Select("SELECT * FROM liked WHERE spotify_id = #{spotifyId} AND music_id = #{musicId}")
    LikeVO isLike(String spotifyId,String musicId);

    // 2. 처음 로그인한 사용자라면 DB에 인서트
    @Insert("INSERT INTO liked (spotify_id,music_id,title,artist,albumImage,duration) VALUES (#{spotifyId},#{musicId},#{title},#{artist},#{albumImage},#{duration})")
    int insertLike(LikeVO vo);

    // 3. 이미 가입된 사용자라면 정보를 최신으로 업데이트
    @Delete("Delete FROM liked WHERE spotify_id = #{spotifyId} AND music_id=#{musicId}")
    int deleteLike(String spotifyId , String musicId);
}
