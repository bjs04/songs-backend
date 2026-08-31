package com.songs.repositories;


import com.songs.models.Song;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SongRepository extends JpaRepository<Song, String> {
 
    List<Song> findByTitleContainingIgnoreCase(String query);

    // Extracts the numeric part after the prefix (e.g., F224 -> 224) and finds the maximum
    @Query(value = "SELECT MAX(CAST(SUBSTRING(song_number, 2) AS UNSIGNED)) " +
                   "FROM songs WHERE song_number LIKE CONCAT(:prefix, '%')", 
           nativeQuery = true)
    Integer findMaxSongNumberByPrefix(@Param("prefix") String prefix);

    @Modifying
    @Transactional
    @Query("UPDATE Song s SET s.dateLastSung = :date WHERE s.songNumber IN :songNumbers")
    int updateDateLastSung(@Param("songNumbers") List<String> songNumbers, @Param("date") java.time.LocalDate date);

    //The Stash Query (Run this BEFORE updating)
    @Modifying
    @Transactional
    @Query("UPDATE Song s SET s.previousLastSungDate = s.dateLastSung WHERE s.songNumber IN :songNumbers")
    int stashPreviousDates(@Param("songNumbers") List<String> songNumbers);

    //The Undo Query (Run this when reverting)
    @Modifying
    @Transactional
    @Query("UPDATE Song s SET s.dateLastSung = s.previousLastSungDate WHERE s.songNumber IN :songNumbers")
    int undoLastSungDates(@Param("songNumbers") List<String> songNumbers);
}