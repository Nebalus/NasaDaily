package dev.nebalus.nasadaily.repository;

import dev.nebalus.nasadaily.Entry;
import dev.nebalus.library.jlogger.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class CacheRepository {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Path cacheDir;
    private final Logger logger;
    private final String jdbcUrl;

    public CacheRepository(Path cacheDir, Logger logger) {
        this.cacheDir = cacheDir;
        this.logger = logger;
        
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            logger.error("Failed to create cache directory", e);
        }

        Path dbFile = cacheDir.resolve("apod.db");
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        
        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement()) {
            
            String sql = "CREATE TABLE IF NOT EXISTS apod_entries (" +
                         "date TEXT PRIMARY KEY," +
                         "title TEXT," +
                         "mediaType TEXT," +
                         "url TEXT," +
                         "hdurl TEXT," +
                         "explanation TEXT" +
                         ");";
            stmt.execute(sql);
        } catch (SQLException e) {
            logger.error("Failed to initialize SQLite database", e);
        }
    }


    public Entry get(LocalDate date) {
        String sql = "SELECT date, title, mediaType, url, hdurl, explanation FROM apod_entries WHERE date = ?";
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, date.format(DATE_FORMAT));
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                Entry entry = new Entry(
                    LocalDate.parse(rs.getString("date"), DATE_FORMAT),
                    rs.getString("title"),
                    rs.getString("mediaType"),
                    rs.getString("url"),
                    rs.getString("hdurl"),
                    rs.getString("explanation")
                );
                logger.debug("Cache hit: " + date);
                return entry;
            }
            
            return null;
        } catch (Exception e) {
            logger.warning("Error reading cache entry for " + date + ", will re-fetch");
            return null;
        }
    }

    public void put(Entry entry) {
        String sql = "INSERT OR REPLACE INTO apod_entries(date, title, mediaType, url, hdurl, explanation) VALUES(?,?,?,?,?,?)";
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, entry.date().format(DATE_FORMAT));
            pstmt.setString(2, entry.title());
            pstmt.setString(3, entry.mediaType());
            pstmt.setString(4, entry.url());
            pstmt.setString(5, entry.hdurl());
            pstmt.setString(6, entry.explanation());
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Failed to write to cache for " + entry.date());
        }
    }

    public CacheResult getAll(List<LocalDate> dates) {
        List<Entry> cached = new ArrayList<>();
        List<LocalDate> missing = new ArrayList<>();

        for (LocalDate date : dates) {
            Entry entry = get(date);
            if (entry != null) {
                cached.add(entry);
            } else {
                missing.add(date);
            }
        }

        if (!cached.isEmpty()) {
            logger.info("Cache: " + cached.size() + " entries found, " + missing.size() + " to fetch");
        }

        return new CacheResult(cached, missing);
    }

    public record CacheResult(List<Entry> cached, List<LocalDate> missing) {}
}
