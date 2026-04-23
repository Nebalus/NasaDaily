package dev.nebalus.apod

import dev.nebalus.library.jlogger.Logger
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

class APODImporter(
    private val outputDir: Path,
    private val logger: Logger,
    private val rateLimitSeconds: Long = 30
) {
    private val rateLimitMs = rateLimitSeconds * 1000

    fun importEntry(entry: APODEntry) {
        Files.createDirectories(outputDir)

        if (entry.isImage) {
            importImage(entry)
        } else {
            importVideoLink(entry)
        }
    }

    fun importEntries(entries: List<APODEntry>) {
        Files.createDirectories(outputDir)

        entries.forEachIndexed { index, entry ->
            logger.info("[%d/%d] Processing: %s", index + 1, entries.size, entry)

            val downloaded = try {
                if (entry.isImage) {
                    importImage(entry)
                } else {
                    importVideoLink(entry)
                }
            } catch (e: IOException) {
                logger.error("Error for %s: %s", entry.date, e.message)
                false
            }

            if (downloaded && index < entries.size - 1) {
                rateLimitWait()
            }
        }

        logger.info("Import complete: %d entries processed", entries.size)
    }

    private fun importImage(entry: APODEntry): Boolean {
        val imageUrl = entry.bestImageUrl
        val extension = extractExtension(imageUrl)
        val fileName = "${entry.date}_${sanitizeFilename(entry.title)}$extension"
        val targetPath = resolveEntryPath(entry, fileName)

        if (Files.exists(targetPath)) {
            logger.info("Skipping (already exists): %s", fileName)
            return false
        }

        val tempPath = targetPath.resolveSibling("$fileName.tmp")
        logger.info("Downloading HD image: %s", fileName)
        try {
            URL(imageUrl).openStream().use { input: InputStream ->
                Files.copy(input, tempPath)
            }
            Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.deleteIfExists(tempPath)
            throw e
        }
        logger.info("Saved: %s", targetPath)
        return true
    }

    private fun importVideoLink(entry: APODEntry): Boolean {
        val fileName = "${entry.date}_${sanitizeFilename(entry.title)}.url"
        val targetPath = resolveEntryPath(entry, fileName)

        if (Files.exists(targetPath)) {
            logger.info("Skipping (already exists): %s", fileName)
            return false
        }

        val content = "[InternetShortcut]\nURL=${entry.url}\n"
        Files.writeString(targetPath, content)
        logger.info("Video link saved: %s -> %s", fileName, entry.url)
        return true
    }

    private fun resolveEntryPath(entry: APODEntry, fileName: String): Path {
        val yearMonth = outputDir
            .resolve(entry.date.year.toString())
            .resolve("%02d".format(entry.date.monthValue))
        Files.createDirectories(yearMonth)
        return yearMonth.resolve(fileName)
    }

    private fun rateLimitWait() {
        try {
            logger.debug("Rate limit: waiting %d seconds...", rateLimitSeconds)
            Thread.sleep(rateLimitMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun extractExtension(url: String): String {
        val path = url.split("?")[0]
        val dotIndex = path.lastIndexOf('.')
        return if (dotIndex >= 0) path.substring(dotIndex) else ".jpg"
    }

    private fun sanitizeFilename(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9\\-_ ]"), "")
            .replace(Regex("\\s+"), "_")
            .trim()
}
