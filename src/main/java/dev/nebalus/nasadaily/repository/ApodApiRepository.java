package dev.nebalus.nasadaily.repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApodApiRepository {
    @SuppressWarnings("deprecation")
    private String httpGet(String urlString) throws IOException {
        URL url = new URL(urlString); // 'constructor(p0: String!): URL' is deprecated but standard for standard library compatibility when not forced to URI format
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(120_000);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("HTTP " + status + " from " + urlString);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }
}
