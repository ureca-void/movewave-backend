package com.example.demo.mapper;

import com.example.demo.vo.LikeVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LikeMapper {

    @Select("SELECT * FROM liked WHERE spotify_id = #{spotifyId}")
    List<LikeVO> findLike(@Param("spotifyId") String spotifyId);

    @Select("SELECT * FROM liked WHERE spotify_id = #{spotifyId} AND music_id = #{musicId}")
    LikeVO isLike(
            @Param("spotifyId") String spotifyId,
            @Param("musicId") String musicId
    );

    @Insert("INSERT INTO liked (spotify_id, music_id, title, artist, albumImage, duration) " +
            "VALUES (#{spotifyId}, #{musicId}, #{title}, #{artist}, #{albumImage}, #{duration})")
    int insertLike(LikeVO vo);

    @Delete("DELETE FROM liked WHERE spotify_id = #{spotifyId} AND music_id = #{musicId}")
    int deleteLike(
            @Param("spotifyId") String spotifyId,
            @Param("musicId") String musicId
    );
}