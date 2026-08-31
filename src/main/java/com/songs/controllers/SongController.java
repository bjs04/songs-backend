package com.songs.controllers;

import com.songs.dtos.ParsedSongDTO;
import com.songs.models.Song;
import com.songs.services.BulkImportService;
import com.songs.services.SongService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/songs")
@Validated
public class SongController {

    private static final Logger logger = LoggerFactory.getLogger(SongController.class);
    private final SongService songService;
    private final BulkImportService bulkImportService;

    // The Controller now injects the Service, not the Repository
    public SongController(SongService songService, BulkImportService bulkImportService) {
        this.songService = songService;
        this.bulkImportService = bulkImportService;
    }

    @GetMapping
    public List<Song> getAllSongs() {
        return songService.getAllSongs(); // Hands the job to the Service
    }

    @GetMapping("/{id}")
    public ResponseEntity<Song> getSongById(@PathVariable String id) {
        Optional<Song> song = songService.getSongById(id);
        
        // The Controller still handles the HTTP logic (200 OK vs 404 Not Found)
        return song.map(ResponseEntity::ok)
                   .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateSong(@PathVariable String id,@Valid @RequestBody Song updatedSong) {
        
        Optional<Song> savedSong = songService.updateSong(id, updatedSong);
        
        return savedSong.map(song -> {
            // Create a custom response object holding both the message and the data
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("message", "Song #" + song.getSongNumber() + " updated successfully!");
            responseBody.put("song", song);
            return ResponseEntity.ok(responseBody);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addSong(@Valid @RequestBody Song song) {
        Song savedSong = songService.addSong(song);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("message", "Song #" + savedSong.getSongNumber() + " added successfully!");
        responseBody.put("song", savedSong);

        return new ResponseEntity<>(responseBody, HttpStatus.CREATED);
    }

    @PostMapping("/bulk-parse")
    public ResponseEntity<List<ParsedSongDTO>> parseBulkText(@RequestBody Map<String, String> request) {
        
        String rawText = request.get("text");
        logger.info("Received bulk text for parsing...");
        
        // Safety check just in case the frontend sends an empty string
        if (rawText == null || rawText.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Send the text to Gemini and get the structured List back
        List<ParsedSongDTO> parsedSongs = bulkImportService.extractSongsFromText(rawText);
        logger.info("Parsed {} songs from the bulk text.", parsedSongs.size());
        
        return ResponseEntity.ok(parsedSongs);
    }

    @PostMapping("/bulk-insert")
    public ResponseEntity<List<Song>> createBulkSongs(@Valid @RequestBody List<Song> bulkSongs) {

        List<Song> savedSongs = songService.saveBulkSongs(bulkSongs);

        return new ResponseEntity<>(savedSongs, HttpStatus.CREATED);
    }
}