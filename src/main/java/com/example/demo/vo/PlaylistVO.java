package com.example.demo.vo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistVO {
	private int id;
	private int userId;
	private String title;
	private String detail;
}
