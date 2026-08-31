package com.songs.services;

import com.songs.repositories.SongRepository;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service 
public class SongAgentTools {

    private static final Logger logger = LoggerFactory.getLogger(SongAgentTools.class);
    private final SongRepository songRepository;
    private final VectorStore vectorStore;

    public SongAgentTools(SongRepository songRepository, VectorStore vectorStore) {
        this.songRepository = songRepository;
        this.vectorStore = vectorStore;
    }

    @Transactional
    @Tool(description = "Updates the 'dateLastSung' field for a given list of song numbers in the database. Use this when the user asks to mark songs as sung, updated, or played on a specific date." +
                        "CRITICAL: The 'dateString' parameter MUST be in strictly 'yyyy-MM-dd' format.")
    public String updateLastSungDate(List<String> songNumbers, String dateString) {
        
        try {
            LocalDate parsedDate = LocalDate.parse(dateString);

            songRepository.stashPreviousDates(songNumbers);
            int updatedCount = songRepository.updateDateLastSung(songNumbers, parsedDate);
            
            // Log it so you can see it in your Spring Boot terminal
            logger.info("Update Date Tool executed. Rows updated in DB: {}", updatedCount);
            
            if (updatedCount == 0) {
                return String.format("FAILED: None of the requested song numbers %s exist in the database.", songNumbers);
            } else if (updatedCount < songNumbers.size()) {
                return String.format("PARTIAL_SUCCESS: Only %d out of %d songs (%s) were updated to %s.", 
                        updatedCount, songNumbers.size(), songNumbers, parsedDate);
            }
            
            return String.format("SUCCESS: All requested songs %s were successfully updated to %s.", songNumbers, parsedDate);
            
        } catch (DateTimeParseException e) {
            return "STATUS: ERROR. Invalid date format. Expected yyyy-MM-dd.";
        }
    }

    @Tool(description = """
                Performs semantic RAG search across choir song lyrics and metadata to find songs by theme, meaning, topic (e.g. grace, communion, forgiveness, peace), or partial phrases.
                Returns an ordered list of matching song numbers ranked from most relevant to least relevant.
            """)
    public List<String> searchSongsByTheme(String searchQuery) {
        
        // 1. Query the vector store for the top 15 matches
        List<Document> similarDocs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(searchQuery)
                .topK(15)
                .build()
        );
        // 2. Extract only the song numbers in exact ranked order
        List<String> songNumbers = new ArrayList<>();
        for (Document doc : similarDocs) {
            // Checks metadata first, fallback to document ID
            String songNumber = doc.getId(); 
            
            if (songNumber != null && !songNumber.isBlank()) {
                songNumbers.add(songNumber);
            }
        }
        
        logger.info("RAG search found ranked song numbers: {}", songNumbers);
        return songNumbers;
    }

    @Tool(description = """
        Use this tool ONLY to "undo", "revert", or "cancel" a recent date update for a list of songs.
        """)
    public String undoLastSungDateUpdate(List<String> songNumbers) {
        try {
            // Run the bulk undo query
            int revertedCount = songRepository.undoLastSungDates(songNumbers);
            
            logger.info("Revert dates tool executed. Rows reverted in DB: {}", revertedCount);
            
            if (revertedCount == 0) {
                return String.format("FAILED: None of the requested song numbers %s could be reverted. They might not have a previous date saved.", songNumbers);
            } else if (revertedCount < songNumbers.size()) {
                return String.format("PARTIAL_SUCCESS: Only %d out of %d songs (%s) were reverted to their previous dates.", 
                        revertedCount, songNumbers.size(), songNumbers);
            }
            
            return String.format("SUCCESS: All requested songs %s were successfully reverted to their previous dates.", songNumbers);
            
        } catch (Exception e) {
            return "STATUS: FAILED. Reason: " + e.getMessage();
        }
    }
}