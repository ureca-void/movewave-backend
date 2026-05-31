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

    public boolean add(LikeVO vo) {
        LikeVO existing = likeMapper.isLike(vo.getSpotifyId(), vo.getMusicId());
        if(existing == null) {
            return likeMapper.insertLike(vo)>0;
        }else{
            return false;
        }
    }

    public boolean remove(String spotifyId, String musicId) {
        return likeMapper.deleteLike(spotifyId,musicId) > 0;
    }
}
