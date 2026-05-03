package dev.nebalus.apod;

import dev.nebalus.apod.repository.CacheRepository;
import dev.nebalus.apod.ui.FetcherUI;
import dev.nebalus.library.jlogger.LogLevel;
import dev.nebalus.library.jlogger.Logger;
import dev.nebalus.library.jlogger.formatter.ColorLineFormatter;
import dev.nebalus.library.jlogger.formatter.colorscheme.DefaultColorScheme;
import dev.nebalus.library.jlogger.handler.SyslogHandler;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ApodCore {
    private static final String API_KEY = "DEMO_KEY";
    private static final Path OUTPUT_DIR = Paths.get("apod_images");
    private static final Path CACHE_DIR = Paths.get(".apod_cache");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public static void main(String[] args) {
        if (args.length > 0 && "--gui".equals(args[0])) {
            SwingUtilities.invokeLater(() -> {
                new FetcherUI().setVisible(true);
            });
            return;
        }

        System.out.println(
            " _    ____   ___  ____  _____    _       _               \n" +
            "/ \\  |  _ \\ / _ \\|  _ \\|  ___|__| |_ ___| |__   ___ _ __ \n" +
            "  / _ \\ | |_) | | | | | | | |_ / _ \\ __/ __| '_ \\ / _ \\ '__|\n" +
            " / ___ \\|  __/| |_| | |_| |  _|  __/ || (__| | | |  __/ |   \n" +
            "/_/   \\_\\_|    \\___/|____/|_|  \\___|\\__\\___|_| |_|\\___|_|   "
        );

        Logger logger = new Logger("APODFetcher");

        SyslogHandler handler = new SyslogHandler(LogLevel.DEBUG, false);
        handler.setFormatter(new ColorLineFormatter(new DefaultColorScheme(), null, "HH:mm:ss"));
        logger.pushHandler(handler);

        CacheRepository cache = new CacheRepository(CACHE_DIR, logger);
        Client client = new Client(API_KEY, logger, cache);

        long rateLimit = 30L;
        List<String> argList = Arrays.asList(args);
        int rateLimitIndex = argList.indexOf("--rate-limit");
        
        List<String> filteredArgs = new ArrayList<>();
        if (rateLimitIndex >= 0 && rateLimitIndex + 1 < argList.size()) {
            try {
                rateLimit = Long.parseLong(argList.get(rateLimitIndex + 1));
            } catch (NumberFormatException ignored) {}
            for (int i = 0; i < argList.size(); i++) {
                if (i != rateLimitIndex && i != rateLimitIndex + 1) {
                    filteredArgs.add(argList.get(i));
                }
            }
        } else {
            filteredArgs.addAll(argList);
        }

        Importer importer = new Importer(OUTPUT_DIR, logger, rateLimit);

        try {
            if (filteredArgs.isEmpty()) {
                Entry entry = client.fetchToday();
                importer.importEntry(entry);
            } else if (filteredArgs.size() == 2 && "--date".equals(filteredArgs.get(0))) {
                LocalDate date = parseDate(filteredArgs.get(1));
                Entry entry = client.fetchByDate(date);
                importer.importEntry(entry);
            } else if (filteredArgs.size() >= 3 && "--from".equals(filteredArgs.get(0))) {
                LocalDate from = parseDate(filteredArgs.get(1));
                LocalDate to = LocalDate.now();
                if (filteredArgs.size() >= 4 && "--to".equals(filteredArgs.get(2))) {
                    to = parseDate(filteredArgs.get(3));
                }
                List<Entry> entries = client.fetchRange(from, to);
                importer.importEntries(entries);
            } else if (filteredArgs.size() == 2 && "--schedule".equals(filteredArgs.get(0))) {
                LocalTime time = parseTime(filteredArgs.get(1));
                runScheduled(time, client, importer, logger);
            } else {
                printUsage(logger);
            }
        } catch (Exception e) {
            logger.error(e);
        } finally {
            if (filteredArgs.isEmpty() || !"--schedule".equals(filteredArgs.get(0))) {
                logger.close();
            }
        }
    }

    private static void runScheduled(LocalTime time, Client client, Importer importer, Logger logger) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "APODScheduler");
            thread.setDaemon(false);
            return thread;
        });

        logger.info("Scheduler started. Daily import at " + time.format(TIME_FMT));

        Runnable task = () -> {
            try {
                logger.info("Scheduled import triggered");
                Entry entry = client.fetchToday();
                importer.importEntry(entry);
            } catch (Exception e) {
                logger.error("Scheduled import failed: " + e.getMessage());
            }
        };

        long initialDelay = computeInitialDelay(time);
        long period = TimeUnit.DAYS.toSeconds(1);

        logger.info("Next import in " + TimeUnit.SECONDS.toMinutes(initialDelay) + " minutes");
        scheduler.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down scheduler...");
            scheduler.shutdown();
            logger.close();
        }));
    }

    private static long computeInitialDelay(LocalTime targetTime) {
        LocalTime now = LocalTime.now();
        Duration delay = Duration.between(now, targetTime);
        if (delay.isNegative()) {
            delay = delay.plusDays(1);
        }
        return delay.getSeconds();
    }

    private static LocalDate parseDate(String input) {
        try {
            return LocalDate.parse(input, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date: " + input + " (expected: YYYY-MM-DD)");
        }
    }

    private static LocalTime parseTime(String input) {
        try {
            return LocalTime.parse(input, TIME_FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid time: " + input + " (expected: HH:mm)");
        }
    }

    private static void printUsage(Logger logger) {
        logger.info("Usage:");
        logger.info("  (no arguments)           -> Import today's APOD");
        logger.info("  --date 2024-01-01        -> Import a single historical APOD");
        logger.info("  --from 2024-01-01 --to 2024-01-31 -> Import a date range");
        logger.info("  --schedule 08:00         -> Run daily import at the specified time");
        logger.info("  --rate-limit 30           -> Set seconds between downloads (default: 30)");
        logger.info("  --gui                     -> Launch graphical user interface");
    }
}
