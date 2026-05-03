package dev.nebalus.apod;

import dev.nebalus.apod.helper.FileHelper;
import dev.nebalus.library.jlogger.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class Importer {
    private final Path outputDir;
    private final Logger logger;
    private final long rateLimitSeconds;
    private final long rateLimitMs;

    public Importer(Path outputDir, Logger logger, long rateLimitSeconds) {
        this.outputDir = outputDir;
        this.logger = logger;
        this.rateLimitSeconds = rateLimitSeconds;
        this.rateLimitMs = rateLimitSeconds * 1000L;
    }

    public Importer(Path outputDir, Logger logger) {
        this(outputDir, logger, 30);
    }

    public void importEntry(Entry entry) {
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            logger.error("Failed to create output directory " + outputDir, e);
            return;
        }

        try {
            if (entry.isImage()) {
                importImage(entry);
            } else {
                importVideoLink(entry);
            }
        } catch (IOException e) {
            logger.error("Error for " + entry.date() + ": " + e.getMessage());
        }
    }

    public void importEntries(List<Entry> entries) {
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            logger.error("Failed to create output directory " + outputDir, e);
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            logger.info(String.format("[%d/%d] Processing: %s", i + 1, entries.size(), entry));

            boolean downloaded = false;
            try {
                if (entry.isImage()) {
                    downloaded = importImage(entry);
                } else {
                    downloaded = importVideoLink(entry);
                }
            } catch (IOException e) {
                logger.error(String.format("Error for %s: %s", entry.date(), e.getMessage()));
            }

            if (downloaded && i < entries.size() - 1) {
                rateLimitWait();
            }
        }

        logger.info(String.format("Import complete: %d entries processed", entries.size()));
    }

    private boolean importImage(Entry entry) throws IOException {
        String imageUrl = entry.getBestImageUrl();
        String extension = FileHelper.extractExtension(imageUrl);
        String fileName = entry.date() + "_" + FileHelper.sanitizeFilename(entry.title()) + extension;
        Path targetPath = resolveEntryPath(entry, fileName);

        if (Files.exists(targetPath)) {
            logger.info(String.format("Skipping (already exists): %s", fileName));
            return false;
        }

        Path tempPath = targetPath.resolveSibling(fileName + ".tmp");
        logger.info(String.format("Downloading HD image: %s", fileName));
        try {
            @SuppressWarnings("deprecation")
            URL url = new URL(imageUrl);
            try (InputStream input = url.openStream()) {
                Files.copy(input, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.deleteIfExists(tempPath);
            throw new IOException("Failed downloading image " + fileName, e);
        }
        logger.info(String.format("Saved: %s", targetPath));
        return true;
    }

    private boolean importVideoLink(Entry entry) throws IOException {
        String fileName = entry.date() + "_" + FileHelper.sanitizeFilename(entry.title()) + ".url";
        Path targetPath = resolveEntryPath(entry, fileName);

        if (Files.exists(targetPath)) {
            logger.info(String.format("Skipping (already exists): %s", fileName));
            return false;
        }

        String content = "[InternetShortcut]\nURL=" + entry.url() + "\n";
        Files.writeString(targetPath, content);
        logger.info(String.format("Video link saved: %s -> %s", fileName, entry.url()));
        return true;
    }

    private Path resolveEntryPath(Entry entry, String fileName) throws IOException {
        Path yearMonth = outputDir
            .resolve(String.valueOf(entry.date().getYear()))
            .resolve(String.format("%02d", entry.date().getMonthValue()));
        Files.createDirectories(yearMonth);
        return yearMonth.resolve(fileName);
    }

    private void rateLimitWait() {
        try {
            logger.debug(String.format("Rate limit: waiting %d seconds...", rateLimitSeconds));
            Thread.sleep(rateLimitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
