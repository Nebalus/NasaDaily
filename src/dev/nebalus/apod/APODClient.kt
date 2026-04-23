package dev.nebalus.apod

import dev.nebalus.library.jlogger.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class APODClient(
    private val apiKey: String,
    private val logger: Logger,
    private val cache: APODCache? = null
) {
    companion object {
        private const val BASE_URL = "https://api.nasa.gov/planetary/apod"
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }

    fun fetchToday(): APODEntry {
        val today = LocalDate.now()
        cache?.get(today)?.let { return it }

        logger.info("Fetching today's APOD...")
        val entry = parseEntry(JSONObject(httpGet("$BASE_URL?api_key=$apiKey")))
        cache?.put(entry)
        return entry
    }

    fun fetchByDate(date: LocalDate): APODEntry {
        cache?.get(date)?.let { return it }

        logger.info("Fetching APOD for %s...", date)
        val entry = parseEntry(JSONObject(httpGet("$BASE_URL?api_key=$apiKey&date=${date.format(DATE_FORMAT)}")))
        cache?.put(entry)
        return entry
    }

    fun fetchRange(start: LocalDate, end: LocalDate): List<APODEntry> {
        val allDates = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .toList()

        if (cache != null) {
            val (cached, missing) = cache.getAll(allDates)

            if (missing.isEmpty()) {
                logger.info("All %d entries served from cache", cached.size)
                return cached.sortedBy { it.date }
            }

            val fetched = if (missing.size == 1) {
                logger.info("Fetching 1 missing APOD for %s...", missing.first())
                val json = httpGet("$BASE_URL?api_key=$apiKey&date=${missing.first().format(DATE_FORMAT)}")
                listOf(parseEntry(JSONObject(json)))
            } else {
                val minDate = missing.min()
                val maxDate = missing.max()
                logger.info("Fetching %d missing APODs from %s to %s...", missing.size, minDate, maxDate)
                val url = "$BASE_URL?api_key=$apiKey&start_date=${minDate.format(DATE_FORMAT)}&end_date=${maxDate.format(DATE_FORMAT)}"
                val array = JSONArray(httpGet(url))
                (0 until array.length())
                    .map { parseEntry(array.getJSONObject(it)) }
                    .filter { it.date in missing }
            }

            fetched.forEach { cache.put(it) }
            return (cached + fetched).sortedBy { it.date }
        }

        logger.info("Fetching APODs from %s to %s...", start, end)
        val url = "$BASE_URL?api_key=$apiKey&start_date=${start.format(DATE_FORMAT)}&end_date=${end.format(DATE_FORMAT)}"
        val array = JSONArray(httpGet(url))
        val entries = (0 until array.length()).map { parseEntry(array.getJSONObject(it)) }
        logger.info("Fetched %d APODs", entries.size)
        return entries
    }

    private fun parseEntry(json: JSONObject): APODEntry = APODEntry(
        date = LocalDate.parse(json.getString("date"), DATE_FORMAT),
        title = json.optString("title", "Untitled"),
        mediaType = json.optString("media_type", "image"),
        url = json.optString("url", ""),
        hdurl = json.optString("hdurl", null),
        explanation = json.optString("explanation", "")
    )

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
        }

        val status = conn.responseCode
        if (status != 200) {
            throw java.io.IOException("HTTP $status from $urlString")
        }

        return BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
            buildString {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    append(line)
                }
            }
        }
    }
}
