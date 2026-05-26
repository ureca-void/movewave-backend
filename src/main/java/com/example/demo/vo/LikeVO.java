package com.example.demo.vo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikeVO {
	private int userId;
	private String musicId;
	private String savedAt;
}
