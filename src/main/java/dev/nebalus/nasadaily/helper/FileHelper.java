package dev.nebalus.nasadaily.helper;

public class FileHelper {
    public static String extractExtension(String url) {
        String path = url.split("\\?")[0];
        int dotIndex = path.lastIndexOf('.');
        return dotIndex >= 0 ? path.substring(dotIndex) : ".jpg";
    }

    public static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\-_ ]", "")
                .replaceAll("\\s+", "_")
                .trim();
    }
}
