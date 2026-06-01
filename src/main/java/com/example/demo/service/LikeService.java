package com.example.demo.service;

import com.example.demo.mapper.LikeMapper;
import com.example.demo.vo.LikeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LikeService {

    @Autowired
    private LikeMapper likeMapper;

    public List<LikeVO> getLike(String spotifyId) {
        return likeMapper.findLike(spotifyId);
    }

    // 좋아요 추가
    public boolean add(LikeVO vo) {
        LikeVO existing = likeMapper.isLike(vo.getSpotifyId(), vo.getMusicId());

        if (existing == null) {
            return likeMapper.insertLike(vo) > 0;
        }

        return false;
    }

    // 좋아요 여부 확인
    public boolean exists(LikeVO vo) {
        LikeVO existing = likeMapper.isLike(vo.getSpotifyId(), vo.getMusicId());

        return existing != null;

        if(existing == null) {
            return false;
        }else{
            return true;
        }

    }

    // 좋아요 삭제
    public boolean remove(String spotifyId, String musicId) {
        return likeMapper.deleteLike(spotifyId, musicId) > 0;
    }
}