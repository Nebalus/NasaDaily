package dev.nebalus.apod

import dev.nebalus.library.jlogger.Logger
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class APODCache(
    private val cacheDir: Path,
    private val logger: Logger
) {
    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
    }

    init {
        Files.createDirectories(cacheDir)
    }

    fun get(date: LocalDate): APODEntry? {
        val file = cacheFile(date)
        if (!Files.exists(file)) return null

        return try {
            val json = JSONObject(Files.readString(file))
            val entry = APODEntry(
                date = LocalDate.parse(json.getString("date"), DATE_FORMAT),
                title = json.getString("title"),
                mediaType = json.getString("media_type"),
                url = json.getString("url"),
                hdurl = json.optString("hdurl", null),
                explanation = json.optString("explanation", "")
            )
            logger.debug("Cache hit: %s", date)
            entry
        } catch (e: Exception) {
            logger.warning("Corrupt cache entry for %s, will re-fetch", date)
            Files.deleteIfExists(file)
            null
        }
    }

    fun put(entry: APODEntry) {
        val json = JSONObject().apply {
            put("date", entry.date.format(DATE_FORMAT))
            put("title", entry.title)
            put("media_type", entry.mediaType)
            put("url", entry.url)
            put("hdurl", entry.hdurl ?: JSONObject.NULL)
            put("explanation", entry.explanation)
        }

        val file = cacheFile(entry.date)
        Files.createDirectories(file.parent)
        Files.writeString(file, json.toString(2))
    }

    fun getAll(dates: List<LocalDate>): Pair<List<APODEntry>, List<LocalDate>> {
        val cached = mutableListOf<APODEntry>()
        val missing = mutableListOf<LocalDate>()

        for (date in dates) {
            val entry = get(date)
            if (entry != null) cached.add(entry) else missing.add(date)
        }

        if (cached.isNotEmpty()) {
            logger.info("Cache: %d entries found, %d to fetch", cached.size, missing.size)
        }

        return Pair(cached, missing)
    }

    private fun cacheFile(date: LocalDate): Path =
        cacheDir.resolve("${date.format(DATE_FORMAT)}.json")
}
