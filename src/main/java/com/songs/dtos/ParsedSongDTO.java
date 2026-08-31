package com.songs.dtos;

public record ParsedSongDTO(
    String title,
    String lyrics,
    String language,
    String category
) {}