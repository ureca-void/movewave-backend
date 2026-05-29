package com.example.demo.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MusicCardDTO {
    private String id;
    private int rank;
    private String title;
    private String description;
    private String cover;
    private int popularity;
}
