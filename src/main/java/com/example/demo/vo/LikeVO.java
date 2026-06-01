package com.example.demo.vo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikeVO {
    private String spotifyId;
    private String musicId;
    private String savedAt;
    private String title;
    private String artist;
    private String albumImage;
    private Integer duration;
}