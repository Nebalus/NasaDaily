package dev.nebalus.nasadaily;

import dev.nebalus.nasadaily.repository.CacheRepository;
import dev.nebalus.library.jlogger.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class Client {
    private static final String BASE_URL = "https://api.nasa.gov/planetary/apod";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final String apiKey;
    private final Logger logger;
    private final CacheRepository cache;

    public Client(String apiKey, Logger logger, CacheRepository cache) {
        this.apiKey = apiKey;
        this.logger = logger;
        this.cache = cache;
    }

    public Client(String apiKey, Logger logger) {
        this(apiKey, logger, null);
    }

    public Entry fetchToday() {
        LocalDate today = LocalDate.now();
        if (cache != null) {
            Entry cached = cache.get(today);
            if (cached != null) return cached;
        }

        logger.info("Fetching today's APOD...");
        try {
            Entry entry = parseEntry(new JSONObject(httpGet(BASE_URL + "?api_key=" + apiKey)));
            if (cache != null) {
                cache.put(entry);
            }
            return entry;
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch today's APOD", e);
        }
    }

    public Entry fetchByDate(LocalDate date) {
        if (cache != null) {
            Entry cached = cache.get(date);
            if (cached != null) return cached;
        }

        logger.info("Fetching APOD for " + date + "...");
        try {
            String url = BASE_URL + "?api_key=" + apiKey + "&date=" + date.format(DATE_FORMAT);
            Entry entry = parseEntry(new JSONObject(httpGet(url)));
            if (cache != null) {
                cache.put(entry);
            }
            return entry;
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch APOD for " + date, e);
        }
    }

    public List<Entry> fetchRange(LocalDate start, LocalDate end) {
        List<LocalDate> allDates = start.datesUntil(end.plusDays(1)).collect(Collectors.toList());

        if (cache != null) {
            CacheRepository.CacheResult result = cache.getAll(allDates);
            List<Entry> cached = result.cached();
            List<LocalDate> missing = result.missing();

            if (missing.isEmpty()) {
                logger.info("All " + cached.size() + " entries served from cache");
                cached.sort(Comparator.comparing(Entry::date));
                return cached;
            }

            List<Entry> fetched = new ArrayList<>();
            try {
                if (missing.size() == 1) {
                    LocalDate missingDate = missing.get(0);
                    logger.info("Fetching 1 missing APOD for " + missingDate + "...");
                    String url = BASE_URL + "?api_key=" + apiKey + "&date=" + missingDate.format(DATE_FORMAT);
                    fetched.add(parseEntry(new JSONObject(httpGet(url))));
                } else {
                    LocalDate minDate = missing.stream().min(LocalDate::compareTo).get();
                    LocalDate maxDate = missing.stream().max(LocalDate::compareTo).get();
                    logger.info("Fetching " + missing.size() + " missing APODs from " + minDate + " to " + maxDate + "...");
                    
                    String url = BASE_URL + "?api_key=" + apiKey + 
                                 "&start_date=" + minDate.format(DATE_FORMAT) + 
                                 "&end_date=" + maxDate.format(DATE_FORMAT);
                    
                    JSONArray array = new JSONArray(httpGet(url));
                    for (int i = 0; i < array.length(); i++) {
                        Entry entry = parseEntry(array.getJSONObject(i));
                        if (missing.contains(entry.date())) {
                            fetched.add(entry);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch range missing APODs", e);
            }

            for (Entry entry : fetched) {
                cache.put(entry);
            }

            List<Entry> combined = new ArrayList<>(cached);
            combined.addAll(fetched);
            combined.sort(Comparator.comparing(Entry::date));
            return combined;
        }

        logger.info("Fetching APODs from " + start + " to " + end + "...");
        try {
            String url = BASE_URL + "?api_key=" + apiKey + 
                         "&start_date=" + start.format(DATE_FORMAT) + 
                         "&end_date=" + end.format(DATE_FORMAT);
            
            JSONArray array = new JSONArray(httpGet(url));
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                entries.add(parseEntry(array.getJSONObject(i)));
            }
            logger.info("Fetched " + entries.size() + " APODs");
            entries.sort(Comparator.comparing(Entry::date));
            return entries;
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch range", e);
        }
    }

    private String httpGet(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private Entry parseEntry(JSONObject json) {
        return new Entry(
            LocalDate.parse(json.getString("date"), DATE_FORMAT),
            json.optString("title", "Untitled"),
            json.optString("media_type", "image"),
            json.optString("url", ""),
            json.has("hdurl") && !json.isNull("hdurl") ? json.getString("hdurl") : null,
            json.optString("explanation", "")
        );
    }
}
