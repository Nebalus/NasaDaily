package dev.nebalus.apod

import dev.nebalus.library.jlogger.LogLevel
import dev.nebalus.library.jlogger.Logger
import dev.nebalus.library.jlogger.formatter.ColorLineFormatter
import dev.nebalus.library.jlogger.formatter.colorscheme.DefaultColorScheme
import dev.nebalus.library.jlogger.handler.SyslogHandler
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import dev.nebalus.apod.ui.APODFetcherUI
import javax.swing.SwingUtilities

private const val API_KEY = "DEMO_KEY"
private val OUTPUT_DIR = Paths.get("apod_images")
private val CACHE_DIR = Paths.get(".apod_cache")
private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

fun main(args: Array<String>) {
    if (args.isNotEmpty() && args[0] == "--gui") {
        SwingUtilities.invokeLater {
            APODFetcherUI().isVisible = true
        }
        return
    }

    println("""
     _    ____   ___  ____  _____    _       _               
    / \  |  _ \ / _ \|  _ \|  ___|__| |_ ___| |__   ___ _ __ 
   / _ \ | |_) | | | | | | | |_ / _ \ __/ __| '_ \ / _ \ '__|
  / ___ \|  __/| |_| | |_| |  _|  __/ || (__| | | |  __/ |   
 /_/   \_\_|    \___/|____/|_|  \___|\__\___|_| |_|\___|_|   
    """.trimIndent())

    val logger = Logger("APODFetcher")

    val handler = SyslogHandler(LogLevel.DEBUG, false)
    handler.setFormatter(ColorLineFormatter(DefaultColorScheme(), null, "HH:mm:ss"))
    logger.pushHandler(handler)

    val cache = APODCache(CACHE_DIR, logger)
    val client = APODClient(API_KEY, logger, cache)

    val rateLimitIndex = args.indexOf("--rate-limit")
    val rateLimit = if (rateLimitIndex >= 0 && rateLimitIndex + 1 < args.size) {
        args[rateLimitIndex + 1].toLongOrNull() ?: 30L
    } else 30L
    val filteredArgs = args.filterIndexed { i, _ -> i != rateLimitIndex && i != rateLimitIndex + 1 }.toTypedArray()

    val importer = APODImporter(OUTPUT_DIR, logger, rateLimit)

    try {
        when {
            filteredArgs.isEmpty() -> {
                val entry = client.fetchToday()
                importer.importEntry(entry)
            }
            filteredArgs.size == 2 && filteredArgs[0] == "--date" -> {
                val date = parseDate(filteredArgs[1])
                val entry = client.fetchByDate(date)
                importer.importEntry(entry)
            }
            filteredArgs.size >= 3 && filteredArgs[0] == "--from" -> {
                val from = parseDate(filteredArgs[1])
                val to = if (filteredArgs.size >= 4 && filteredArgs[2] == "--to") parseDate(filteredArgs[3]) else LocalDate.now()
                val entries = client.fetchRange(from, to)
                importer.importEntries(entries)
            }
            filteredArgs.size == 2 && filteredArgs[0] == "--schedule" -> {
                val time = parseTime(filteredArgs[1])
                runScheduled(time, client, importer, logger)
            }
            else -> printUsage(logger)
        }
    } catch (e: Exception) {
        logger.error(e)
    } finally {
        if (filteredArgs.isEmpty() || filteredArgs[0] != "--schedule") {
            logger.close()
        }
    }
}

private fun runScheduled(time: LocalTime, client: APODClient, importer: APODImporter, logger: Logger) {
    val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "APODScheduler").apply { isDaemon = false }
    }

    logger.info("Scheduler started. Daily import at %s", time.format(TIME_FMT))

    val task = Runnable {
        try {
            logger.info("Scheduled import triggered")
            val entry = client.fetchToday()
            importer.importEntry(entry)
        } catch (e: Exception) {
            logger.error("Scheduled import failed: %s", e.message)
        }
    }

    val initialDelay = computeInitialDelay(time)
    val period = TimeUnit.DAYS.toSeconds(1)

    logger.info("Next import in %d minutes", TimeUnit.SECONDS.toMinutes(initialDelay))
    scheduler.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.SECONDS)

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down scheduler...")
        scheduler.shutdown()
        logger.close()
    })
}

private fun computeInitialDelay(targetTime: LocalTime): Long {
    val now = LocalTime.now()
    var delay = Duration.between(now, targetTime)
    if (delay.isNegative) {
        delay = delay.plusDays(1)
    }
    return delay.seconds
}

private fun parseDate(input: String): LocalDate = try {
    LocalDate.parse(input, DATE_FMT)
} catch (e: DateTimeParseException) {
    throw IllegalArgumentException("Invalid date: $input (expected: YYYY-MM-DD)")
}

private fun parseTime(input: String): LocalTime = try {
    LocalTime.parse(input, TIME_FMT)
} catch (e: DateTimeParseException) {
    throw IllegalArgumentException("Invalid time: $input (expected: HH:mm)")
}

private fun printUsage(logger: Logger) {
    logger.info("Usage:")
    logger.info("  (no arguments)           -> Import today's APOD")
    logger.info("  --date 2024-01-01        -> Import a single historical APOD")
    logger.info("  --from 2024-01-01 --to 2024-01-31 -> Import a date range")
    logger.info("  --schedule 08:00         -> Run daily import at the specified time")
    logger.info("  --rate-limit 30           -> Set seconds between downloads (default: 30)")
    logger.info("  --gui                     -> Launch graphical user interface")
}
