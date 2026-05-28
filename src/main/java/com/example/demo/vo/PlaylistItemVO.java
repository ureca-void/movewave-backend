package com.example.demo.vo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistItemVO {
	private int id;
	private int listId;
	private String musicId;
	private int sortOrder;
	

}
