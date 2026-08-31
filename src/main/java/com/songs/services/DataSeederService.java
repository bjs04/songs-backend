package com.songs.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.songs.dtos.SongLyricDto;
import com.songs.models.Song;
import com.songs.repositories.SongRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DataSeederService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeederService.class);
    private final SongRepository songRepository;
    private final SongService songService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.vector-store.backfill-on-startup:false}")
    private boolean backfillOnStartup;

    public DataSeederService(SongRepository songRepository, SongService songService) {
        this.songRepository = songRepository;
        this.songService = songService;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean databaseWasEmpty = songRepository.count() == 0;
        
        // -------------------------------------------------------------
        // PART 1: MySQL Seeding
        // -------------------------------------------------------------
        if (databaseWasEmpty) {
            logger.info("Starting Data Seeder: Merging CSV and JSON...");

            List<Song> songsToSave = new ArrayList<>();

            try (CSVReader csvReader = new CSVReaderBuilder(
                    new InputStreamReader(new ClassPathResource("songs_metadata.csv").getInputStream()))
                    .withSkipLines(1).build()) {
                
                List<String[]> records = csvReader.readAll();
                for (String[] record : records) {
                    Song song = new Song();
                    song.setSongNumber(record.length > 0 ? record[0] : null);
                    song.setLegacyOldBook(record.length > 1 ? record[1] : null);
                    song.setLegacyFileNo(record.length > 2 ? record[2] : null);
                    song.setAlternateSearchTags(record.length > 3 ? record[3] : null);
                    song.setLanguage(record.length > 4 ? record[4] : null);
                    song.setTitle(record.length > 5 ? record[5] : null);
                    song.setStyle(record.length > 6 ? record[6] : null);
                    song.setCategory(record.length > 7 ? record[7] : null);
                    song.setTempo(record.length > 8 ? record[8] : null);
                    song.setTranspose(record.length > 9 ? record[9] : null);
                    song.setMultipad(record.length > 10 ? record[10] : null);
                    song.setDateLastSungFromString(record.length > 11 ? record[11] : null);
                    songsToSave.add(song);
                }
            }

            Map<String, Song> songMap = songsToSave.stream()
                    .collect(Collectors.toMap(Song::getSongNumber, s -> s));

            List<SongLyricDto> lyricsList = objectMapper.readValue(
                    new ClassPathResource("song_lyrics.json").getInputStream(),
                    new TypeReference<List<SongLyricDto>>() {}
            );

            for (SongLyricDto lyricDto : lyricsList) {
                Song song = songMap.get(lyricDto.getSongNumber());
                if (song != null) {
                    song.setLyrics(lyricDto.getLyrics());
                } else {
                    logger.warn("Warning: Lyrics found for Song #{} but no matching CSV metadata exists.", lyricDto.getSongNumber());
                }
            }
            
            List<Song> finalSongList = new ArrayList<>(songMap.values());

            // 1. Save all songs to MySQL
            songRepository.saveAll(finalSongList);
            logger.info("SUCCESS: {} songs saved to MySQL!", finalSongList.size());

            // 2. Batch-embed all songs into Pinecone (Runs only once on fresh setup!)
            logger.info("Generating and uploading embeddings to Pinecone Vector DB...");
            songService.embedSongs(finalSongList);
            logger.info("SUCCESS: All song vectors stored in Pinecone!");

        } else {
            logger.info("MySQL database is already populated. Skipping database seeding.");
        }

        if (!databaseWasEmpty && backfillOnStartup) {
            logger.info("Backfilling Pinecone embeddings from MySQL...");
            int embeddedCount = songService.backfillAllEmbeddings();
            logger.info("SUCCESS: {} song embeddings backfilled to Pinecone.", embeddedCount);
        }
    }
}