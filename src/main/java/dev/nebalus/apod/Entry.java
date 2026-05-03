package dev.nebalus.apod;

import java.time.LocalDate;

public record Entry(
    LocalDate date,
    String title,
    String mediaType,
    String url,
    String hdurl,
    String explanation
) {
    public boolean isImage() {
        return "image".equals(mediaType);
    }

    public String getBestImageUrl() {
        if (hdurl != null && !hdurl.trim().isEmpty()) {
            return hdurl;
        }
        return url;
    }

    @Override
    public String toString() {
        return "[" + date + "] " + title + " (" + mediaType + ")";
    }
}
