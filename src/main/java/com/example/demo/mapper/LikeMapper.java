package com.example.demo.mapper;

import com.example.demo.vo.LikeVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LikeMapper {

    @Select("""
        SELECT
            spotify_id AS spotifyId,
            music_id AS musicId
        FROM likes
        WHERE spotify_id = #{spotifyId}
    """)
    List<LikeVO> findLike(@Param("spotifyId") String spotifyId);

    @Select("""
        SELECT
            spotify_id AS spotifyId,
            music_id AS musicId
        FROM likes
        WHERE spotify_id = #{spotifyId}
          AND music_id = #{musicId}
    """)
    LikeVO isLike(
            @Param("spotifyId") String spotifyId,
            @Param("musicId") String musicId
    );
            
    // 2. 처음 로그인한 사용자라면 DB에 인서트
    @Insert("INSERT INTO liked (spotify_id,music_id,title,artist,albumImage,duration) VALUES (#{spotifyId},#{musicId},#{title},#{artist},#{albumImage},#{duration})")
    int insertLike1(LikeVO vo);


    @Delete("""
        DELETE FROM likes
        WHERE spotify_id = #{spotifyId}
          AND music_id = #{musicId}
    """)
    int deleteLike(
            @Param("spotifyId") String spotifyId,
            @Param("musicId") String musicId
    );
}