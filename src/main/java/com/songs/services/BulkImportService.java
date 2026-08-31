package com.songs.services;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.songs.dtos.ParsedSongDTO;

import java.util.List;

@Service
public class BulkImportService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Spring AI automatically injects the ChatClient Builder!
    public BulkImportService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public List<ParsedSongDTO> extractSongsFromText(String rawText) {

        String promptTemplate = """
            You are a data extraction assistant for a Christian Choir Songbook.
            Extract all individual songs from the provided text.
            
            CRITICAL LYRIC FORMATTING RULES:
            1. PRESERVE STRUCTURE: You MUST strictly preserve all musical structural markers present in the source text. 
            2. KEYWORDS: Never delete the words: Chorus, Verse, Bridge, Pre-Chorus.
            3. STANDARDIZATION: Format these structural markers uniformly on their own line, wrapped in brackets.
            4. PRESERVE REPETITION MARKERS: You MUST keep all numbers or symbols that indicate a line should be sung multiple times. Never delete markers such as (2), - 2, or x2. Leave them exactly where they appear at the end of the line.

            STRICT RULES FOR FIELDS:
            - title: Extract the song title. Convert to Title Case (e.g., "Amazing Grace").
            - lyrics: Extract the lyrics. PRESERVE LINE BREAKS. 
            - language: You MUST return exactly one of these keys based on the text: 'E' (English), 'H' (Hindi), 'T' (Telugu), or 'O' (Other).
            - category: Guess the category and return exactly one of these keys: 'P' (Praise), 'W' (Worship), 'XMAS' (Christmas), 'MARRIG' (Marriage), 'LT' (Lord's Table, ie, breaking of bread and drinking from the cup), or 'OTH' (Other).
            - If the provided text does not contain ANY recognizable song, hymn, or lyrics, return an empty JSON array: [].
            - Return valid JSON. In lyrics strings, encode line breaks as \\n and tabs as \\t; never include raw control characters.
            - Return only the JSON array. Do not add Markdown fences or explanatory text.
            
            TEXT TO PROCESS:
            %s
            """.formatted(rawText);

        // 3. Call Gemini
        String response = chatClient.prompt()
                .user(promptTemplate)
                .call()
                .content();

        try {
            return objectMapper.readValue(
                    escapeControlCharactersInJsonStrings(extractJsonArray(response)),
                    new TypeReference<List<ParsedSongDTO>>() {}
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "The AI returned invalid song JSON: " + exception.getMessage(),
                    exception
            );
        }

    }

    private String extractJsonArray(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("The AI returned an empty response");
        }

        String cleanedResponse = response.trim();
        if (cleanedResponse.startsWith("```")) {
            int firstLineBreak = cleanedResponse.indexOf('\n');
            int closingFence = cleanedResponse.lastIndexOf("```");
            if (firstLineBreak >= 0 && closingFence > firstLineBreak) {
                cleanedResponse = cleanedResponse.substring(firstLineBreak + 1, closingFence).trim();
            }
        }

        int arrayStart = cleanedResponse.indexOf('[');
        int arrayEnd = cleanedResponse.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd < arrayStart) {
            throw new IllegalStateException("The AI response did not contain a JSON array");
        }

        return cleanedResponse.substring(arrayStart, arrayEnd + 1);
    }

    private String escapeControlCharactersInJsonStrings(String json) {
        StringBuilder sanitized = new StringBuilder(json.length());
        boolean insideString = false;
        boolean escaped = false;

        for (int index = 0; index < json.length(); index++) {
            char character = json.charAt(index);

            if (insideString) {
                if (escaped) {
                    escaped = false;
                    sanitized.append(character);
                } else if (character == '\\') {
                    escaped = true;
                    sanitized.append(character);
                } else if (character == '"') {
                    insideString = false;
                    sanitized.append(character);
                } else if (character < 0x20) {
                    switch (character) {
                        case '\n' -> sanitized.append("\\n");
                        case '\r' -> sanitized.append("\\r");
                        case '\t' -> sanitized.append("\\t");
                        case '\b' -> sanitized.append("\\b");
                        case '\f' -> sanitized.append("\\f");
                        default -> sanitized.append(String.format("\\u%04x", (int) character));
                    }
                } else {
                    sanitized.append(character);
                }
            } else {
                sanitized.append(character);
                if (character == '"') {
                    insideString = true;
                }
            }
        }

        return sanitized.toString();
    }
}