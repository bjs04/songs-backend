package com.songs.services;

import com.songs.exceptions.SongAlreadyExistsException;
import com.songs.models.Song;
import com.songs.repositories.SongRepository;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

@Service
public class SongService {

    private static final int PINECONE_BATCH_SIZE = 25;
    private static final Logger logger = LoggerFactory.getLogger(SongService.class);
    private final SongRepository songRepository;
    private final VectorStore vectorStore;

    public SongService(SongRepository songRepository, VectorStore vectorStore) {
        this.songRepository = songRepository;
        this.vectorStore = vectorStore;
    }

    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }

    public Optional<Song> getSongById(String id) {
        return songRepository.findById(id);
    }

    public List<Song> searchSongs(String query) {
        return songRepository.findByTitleContainingIgnoreCase(query);
    }

    // UPDATE AN EXISTING SONG
    @Transactional
    public Optional<Song> updateSong(String originalId, Song updatedSongData) {
        return songRepository.findById(originalId).map(existingSong -> {

            String newId = updatedSongData.getSongNumber();
            boolean idChanged = !originalId.equals(newId);

            // SAFETY CHECK for ID changes
            if (idChanged && songRepository.existsById(newId)) {
                throw new SongAlreadyExistsException("Cannot update: Song number #" + originalId + " to #" + newId + " as it already exists.");
            }

            // OPTIMIZATION CHECK: Did any semantic fields actually change?
            boolean needsReEmbedding = idChanged ||
                    !Objects.equals(existingSong.getTitle(), updatedSongData.getTitle()) ||
                    !Objects.equals(existingSong.getCategory(), updatedSongData.getCategory()) ||
                    !Objects.equals(existingSong.getLanguage(), updatedSongData.getLanguage()) ||
                    !Objects.equals(existingSong.getLyrics(), updatedSongData.getLyrics());

            // 1. Handle Vector Store Updates (Only if necessary!)
            if (needsReEmbedding) {
                deleteVector(originalId);
            }

            // 2. Handle Database Update
            if (idChanged) {
                songRepository.delete(existingSong);
            }
            Song savedSong = songRepository.save(updatedSongData);

            // 3. Create New Vector (Only if necessary!)
            if (needsReEmbedding) {
                embedSong(savedSong);
                logger.info("Vector Store updated for song: {}", newId);
            } else {
                logger.info("Skipped vector embedding (no semantic changes) for song: {}", newId);
            }

            return savedSong;
        });
    }

    // private String getPrefixForLanguage(String language) {
        
    //     switch (language.trim().toLowerCase()) {
    //         case "h":
    //             return "H";
    //         case "telugu":
    //             return "T";
    //         case "english":
    //         default:
    //             return "F";
    //         }
    //     }
        
    // For bulk save to DB without embedding
    private Song saveToDatabaseOnly(Song song) {
        if (song.getLanguage() == null || song.getLanguage().trim().isEmpty()) {
            throw new IllegalArgumentException("Song language is strictly required to generate a song number.");
        }
        String prefix = (song.getLanguage().equalsIgnoreCase("E")) ? "F" : song.getLanguage();
        Integer maxNumber = songRepository.findMaxSongNumberByPrefix(prefix);
        int nextNumber = (maxNumber == null) ? 1 : maxNumber + 1;
        song.setSongNumber(prefix + nextNumber);
        
        logger.info("Saving song to database with number: {} - {}", song.getSongNumber(), song.getTitle());
        return songRepository.save(song);
    }

    @Transactional
    public Song addSong(Song song) {

        Song saved = saveToDatabaseOnly(song);
        embedSong(saved); // Automatically update vector store on single save!
        return saved;
    }


    @Transactional
    public List<Song> saveBulkSongs(List<Song> songsToSave) {
        List<Song> savedSongs = new ArrayList<>();
        
        for (Song song : songsToSave) {
            savedSongs.add(saveToDatabaseOnly(song));
        }

        embedSongs(savedSongs);
        return savedSongs;
    }   

    public void embedSong(Song song) {
        vectorStore.add(List.of(createDocument(song)));
    }

    public void embedSongs(List<Song> songs) {
        List<Document> documents = songs.stream()
                .map(this::createDocument)
                .toList();

        for (int start = 0; start < documents.size(); start += PINECONE_BATCH_SIZE) {
            int end = Math.min(start + PINECONE_BATCH_SIZE, documents.size());
            vectorStore.add(documents.subList(start, end));
            logger.info("Uploaded Pinecone batch {}-{} of {} documents.",
                    start + 1, end, documents.size());
        }
    }

    private Document createDocument(Song song) {
        String contentToEmbed = String.format(
                "Title: %s\nCategory: %s\nLanguage: %s\nLyrics:\n%s",
                song.getTitle() != null ? song.getTitle() : "",
                song.getCategory() != null ? song.getCategory() : "",
                languageDisplayName(song.getLanguage()),
                song.getLyrics() != null ? song.getLyrics() : ""
        );

        Map<String, Object> metadata = Map.of(
                "songNumber", song.getSongNumber(),
                "alternateNumber", song.getAlternateSearchTags() != null
                        ? song.getAlternateSearchTags()
                        : ""
        );

        return new Document(song.getSongNumber(), contentToEmbed, metadata);
    }

    private String languageDisplayName(String language) {
        if (language == null || language.isBlank()) {
            return "";
        }

        return switch (language.trim().toUpperCase()) {
            case "E" -> "English";
            case "H" -> "Hindi";
            case "T" -> "Telugu";
            case "O" -> "Other";
            default -> language;
        };
    }

    private void deleteVector(String songNumber) {
        try {
            vectorStore.delete(List.of(songNumber));
        } catch (Exception e) {
            // Safe fallback if the vector did not exist previously
            logger.warn("Warning: Could not delete vector for ID {}: {}", songNumber, e.getMessage());
        }
    }

    public int backfillAllEmbeddings() {
        List<Song> allSongs = songRepository.findAll();
        if (!allSongs.isEmpty()) {
            embedSongs(allSongs);
        }
        return allSongs.size();
    }
}