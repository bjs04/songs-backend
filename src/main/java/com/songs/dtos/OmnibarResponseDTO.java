package com.songs.dtos;

import java.util.List;

public record OmnibarResponseDTO(
    String type,      // "ACTION", "SEARCH_RESULTS", or "MESSAGE"
    String message,   // Human-readable message (AI's response) to display in the Toast/Banner
    List<String> songs // The actual songs (only populated if type == SEARCH_RESULTS)
) {}