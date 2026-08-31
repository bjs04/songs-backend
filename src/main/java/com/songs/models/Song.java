package com.songs.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "songs")
public class Song {

    @Id
    @Column(name = "song_number", length = 20)
    private String songNumber; // This is the primary key from your CSV/JSON

    @Column(name = "legacy_old_book", length = 20)
    private String legacyOldBook;

    @Column(name = "legacy_file_no", length = 20)
    private String legacyFileNo;

    @Column(name = "alternate_search_tags")
    private String alternateSearchTags;

    @NotBlank(message = "Language is strictly required.")
    @Column(name = "language", length = 5)
    private String language;

    @NotBlank(message = "Song title is mandatory.")
    @Column(name = "song_name")
    private String title;

    @Column(name = "style", length = 50)
    private String style;

    @NotBlank(message = "Category must be selected.")
    @Column(name = "category", length = 70)
    private String category;

    @Column(name = "tempo", length = 20)
    private String tempo;

    @Column(name = "transpose", length = 20)
    private String transpose;

    @Column(name = "multipad", length = 50)
    private String multipad;

    @Column(name = "date_last_sung")
    private LocalDate dateLastSung;

    @Column(name = "lyrics", columnDefinition = "TEXT")
    private String lyrics;

    @Column(name = "date_added_to_catalog")
    private LocalDate dateAddedToCatalog;

    @Column(name = "previous_last_sung_date")
    private LocalDate previousLastSungDate;

    // --- CSV DATE PARSING HELPER ---
    public void setDateLastSungFromString(String dateString) {
        if (dateString != null && !dateString.trim().isEmpty()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                this.dateLastSung = LocalDate.parse(dateString.trim(), formatter);
            } catch (DateTimeParseException e) {
                // If it hits a weirdly formatted date in the CSV, it safely leaves it null
                this.dateLastSung = null; 
            }
        }
    }

}