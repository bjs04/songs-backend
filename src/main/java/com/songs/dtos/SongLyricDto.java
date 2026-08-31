package com.songs.dtos; // Update to your package name

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SongLyricDto {
    
    @JsonProperty("song_number")
    private String songNumber;
    
    private String title;
    
    private String lyrics;
}