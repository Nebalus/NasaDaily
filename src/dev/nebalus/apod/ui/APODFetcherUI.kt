package dev.nebalus.apod.ui

import dev.nebalus.apod.*
import dev.nebalus.library.jlogger.LogLevel
import dev.nebalus.library.jlogger.Logger
import dev.nebalus.library.jlogger.formatter.LineFormatter
import dev.nebalus.library.jlogger.handler.SyslogHandler
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.swing.*
import javax.swing.border.EmptyBorder

class APODFetcherUI : JFrame("APODFetcher") {
    companion object {
        private const val API_KEY = "DEMO_KEY"
        private val OUTPUT_DIR = Paths.get("apod_images")
        private val CACHE_DIR = Paths.get(".apod_cache")
        private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
        private val BG_DARK = Color(30, 30, 30)
        private val BG_PANEL = Color(45, 45, 45)
        private val BG_INPUT = Color(60, 60, 60)
        private val FG_TEXT = Color(220, 220, 220)
        private val ACCENT = Color(70, 130, 220)
    }

    private val logger = Logger("APODFetcher")
    private val cache = APODCache(CACHE_DIR, logger)
    private val client = APODClient(API_KEY, logger, cache)
    private val importer = APODImporter(OUTPUT_DIR, logger)

    private val imagePanel = ImagePanel()
    private val titleLabel = JLabel("No APOD loaded")
    private val explanationArea = JTextArea(3, 40)
    private val countLabel = JLabel("")
    private val rateLimitSpinner = JSpinner(SpinnerNumberModel(30, 1, 300, 5))
    private val logArea = JTextArea(8, 60)
    private val progressBar = JProgressBar()
    private val statusLabel = JLabel("Ready")

    private var localFiles: List<File> = emptyList()
    private var currentIndex = -1

    init {
        setupDarkTheme()
        setupLogger()
        buildUI()
        setupWindow()
        refreshLocalFiles()
    }

    private fun setupDarkTheme() {
        UIManager.put("Panel.background", BG_DARK)
        UIManager.put("Label.foreground", FG_TEXT)
        UIManager.put("TextField.background", BG_INPUT)
        UIManager.put("TextField.foreground", FG_TEXT)
        UIManager.put("TextField.caretForeground", FG_TEXT)
        UIManager.put("TextArea.background", BG_INPUT)
        UIManager.put("TextArea.foreground", FG_TEXT)
        UIManager.put("TextArea.caretForeground", FG_TEXT)
        UIManager.put("Button.background", ACCENT)
        UIManager.put("Button.foreground", Color.WHITE)
        UIManager.put("ProgressBar.background", BG_INPUT)
        UIManager.put("ProgressBar.foreground", ACCENT)
        UIManager.put("ScrollPane.background", BG_DARK)
        UIManager.put("Spinner.background", BG_INPUT)
        UIManager.put("Spinner.foreground", FG_TEXT)
    }

    private fun setupLogger() {
        val consoleHandler = SyslogHandler(LogLevel.DEBUG, true)
        logger.pushHandler(consoleHandler)

        val uiHandler = SwingLogHandler(logArea, LogLevel.DEBUG)
        uiHandler.setFormatter(LineFormatter(null, "HH:mm:ss"))
        logger.pushHandler(uiHandler)
    }

    private fun buildUI() {
        contentPane.layout = BorderLayout(0, 0)
        contentPane.background = BG_DARK

        contentPane.add(imagePanel, BorderLayout.CENTER)
        contentPane.add(buildSidePanel(), BorderLayout.EAST)
        contentPane.add(buildBottomPanel(), BorderLayout.SOUTH)
    }

    private fun buildSidePanel(): JPanel {
        val side = JPanel()
        side.layout = BoxLayout(side, BoxLayout.Y_AXIS)
        side.background = BG_PANEL
        side.border = EmptyBorder(12, 12, 12, 12)
        side.preferredSize = Dimension(340, 0)

        // Title
        titleLabel.font = Font("SansSerif", Font.BOLD, 16)
        titleLabel.foreground = FG_TEXT
        titleLabel.alignmentX = LEFT_ALIGNMENT
        side.add(titleLabel)
        side.add(Box.createVerticalStrut(8))

        // Explanation
        explanationArea.isEditable = false
        explanationArea.lineWrap = true
        explanationArea.wrapStyleWord = true
        explanationArea.background = BG_PANEL
        explanationArea.foreground = Color(180, 180, 180)
        explanationArea.font = Font("SansSerif", Font.PLAIN, 12)
        explanationArea.border = null
        val explScroll = JScrollPane(explanationArea)
        explScroll.border = null
        explScroll.alignmentX = LEFT_ALIGNMENT
        explScroll.preferredSize = Dimension(320, 120)
        side.add(explScroll)
        side.add(Box.createVerticalStrut(16))

        // Browse local images
        side.add(createSectionLabel("Browse (Local)"))
        side.add(Box.createVerticalStrut(4))

        val dateNav = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        dateNav.background = BG_PANEL
        dateNav.alignmentX = LEFT_ALIGNMENT

        val prevBtn = createButton("◄")
        prevBtn.addActionListener { navigateLocal(-1) }
        dateNav.add(prevBtn)

        countLabel.foreground = FG_TEXT
        countLabel.font = Font("SansSerif", Font.PLAIN, 12)
        dateNav.add(countLabel)

        val nextBtn = createButton("►")
        nextBtn.addActionListener { navigateLocal(1) }
        dateNav.add(nextBtn)

        val refreshBtn = createButton("Refresh")
        refreshBtn.addActionListener { refreshLocalFiles() }
        dateNav.add(refreshBtn)

        side.add(dateNav)

        side.add(Box.createVerticalStrut(16))

        // Calendar for import
        side.add(createSectionLabel("Import (Calendar)"))
        side.add(Box.createVerticalStrut(4))

        val calendar = CalendarPanel { from, to -> startRangeImport(from, to) }
        calendar.alignmentX = LEFT_ALIGNMENT
        side.add(calendar)

        side.add(Box.createVerticalStrut(8))

        val ratePanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        ratePanel.background = BG_PANEL
        ratePanel.alignmentX = LEFT_ALIGNMENT
        ratePanel.add(createLabel("Rate limit (s):"))
        ratePanel.add(rateLimitSpinner)
        side.add(ratePanel)

        side.add(Box.createVerticalStrut(8))

        val importTodayBtn = createButton("Import Today")
        importTodayBtn.alignmentX = LEFT_ALIGNMENT
        importTodayBtn.addActionListener { startTodayImport() }
        side.add(importTodayBtn)

        side.add(Box.createVerticalGlue())
        return side
    }

    private fun buildBottomPanel(): JPanel {
        val bottom = JPanel(BorderLayout(0, 4))
        bottom.background = BG_PANEL
        bottom.border = EmptyBorder(8, 12, 8, 12)

        logArea.isEditable = false
        logArea.font = Font("Monospaced", Font.PLAIN, 11)
        logArea.background = Color(25, 25, 25)
        logArea.foreground = Color(160, 220, 160)
        val logScroll = JScrollPane(logArea)
        logScroll.preferredSize = Dimension(0, 150)
        logScroll.border = BorderFactory.createLineBorder(Color(60, 60, 60))

        val logHeader = JPanel(BorderLayout())
        logHeader.background = BG_PANEL
        logHeader.add(createSectionLabel("Log"), BorderLayout.WEST)
        val clearBtn = createButton("Clear")
        clearBtn.addActionListener { logArea.text = "" }
        logHeader.add(clearBtn, BorderLayout.EAST)

        bottom.add(logHeader, BorderLayout.NORTH)
        bottom.add(logScroll, BorderLayout.CENTER)

        val statusBar = JPanel(BorderLayout(8, 0))
        statusBar.background = BG_PANEL
        progressBar.isStringPainted = true
        progressBar.preferredSize = Dimension(200, 20)
        progressBar.background = BG_INPUT
        progressBar.foreground = ACCENT
        statusLabel.foreground = Color(150, 150, 150)
        statusLabel.font = Font("SansSerif", Font.PLAIN, 11)

        statusBar.add(progressBar, BorderLayout.WEST)
        statusBar.add(statusLabel, BorderLayout.CENTER)
        bottom.add(statusBar, BorderLayout.SOUTH)

        return bottom
    }

    private fun setupWindow() {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                logger.close()
                dispose()
            }
        })
        minimumSize = Dimension(1100, 700)
        setSize(1200, 800)
        setLocationRelativeTo(null)
    }

    // --- Local browsing (no API calls) ---

    private fun refreshLocalFiles() {
        localFiles = scanLocalImages().sortedBy { it.name }
        if (localFiles.isEmpty()) {
            currentIndex = -1
            imagePanel.clear()
            titleLabel.text = "No images found"
            explanationArea.text = "Use the Import section to download APODs first."
            countLabel.text = "0 / 0"
            statusLabel.text = "No local images"
        } else {
            currentIndex = localFiles.size - 1 // show latest
            showLocalImage(currentIndex)
        }
        logger.info("Found %d local images", localFiles.size)
    }

    private fun scanLocalImages(): List<File> {
        val dir = OUTPUT_DIR.toFile()
        if (!dir.exists()) return emptyList()

        return dir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".url") && !it.name.endsWith(".tmp") }
            .toList()
    }

    private fun showLocalImage(index: Int) {
        if (index < 0 || index >= localFiles.size) return

        val file = localFiles[index]
        countLabel.text = "${index + 1} / ${localFiles.size}"

        // Try to extract date from filename (YYYY-MM-DD_...)
        val dateStr = file.name.take(10)
        val cachedEntry = try {
            val date = LocalDate.parse(dateStr, DATE_FMT)
            cache.get(date)
        } catch (e: Exception) {
            null
        }

        if (cachedEntry != null) {
            titleLabel.text = "<html><b>${cachedEntry.title}</b></html>"
            explanationArea.text = cachedEntry.explanation
            explanationArea.caretPosition = 0
        } else {
            // Derive title from filename
            val name = file.nameWithoutExtension
                .removePrefix(dateStr)
                .removePrefix("_")
                .replace("_", " ")
            titleLabel.text = "<html><b>$name</b></html>"
            explanationArea.text = ""
        }

        imagePanel.loadFromFile(file)
        imagePanel.setMetadata(cachedEntry)
        statusLabel.text = file.name
    }

    private fun navigateLocal(offset: Int) {
        if (localFiles.isEmpty()) return
        currentIndex = (currentIndex + offset).coerceIn(0, localFiles.size - 1)
        showLocalImage(currentIndex)
    }

    // --- Import actions (API calls) ---

    private fun startTodayImport() {
        statusLabel.text = "Importing today's APOD..."
        object : SwingWorker<Void?, Void>() {
            override fun doInBackground(): Void? {
                try {
                    val entry = client.fetchToday()
                    importer.importEntry(entry)
                } catch (e: Exception) {
                    logger.error(e)
                }
                return null
            }

            override fun done() {
                refreshLocalFiles()
                statusLabel.text = "Ready"
            }
        }.execute()
    }

    private fun startRangeImport(from: LocalDate, to: LocalDate) {
        val rate = (rateLimitSpinner.value as Number).toLong()

        val rateLimitedImporter = APODImporter(OUTPUT_DIR, logger, rate)
        statusLabel.text = "Importing $from to $to..."

        object : SwingWorker<Void?, Int>() {
            override fun doInBackground(): Void? {
                try {
                    val entries = client.fetchRange(from, to)
                    SwingUtilities.invokeLater {
                        progressBar.maximum = entries.size
                        progressBar.value = 0
                    }
                    rateLimitedImporter.importEntries(entries)
                } catch (e: Exception) {
                    logger.error(e)
                }
                return null
            }

            override fun done() {
                progressBar.value = progressBar.maximum
                statusLabel.text = "Import complete"
                refreshLocalFiles()
            }
        }.execute()
    }

    // --- UI helpers ---

    private fun createButton(text: String): JButton {
        val btn = JButton(text)
        btn.background = ACCENT
        btn.foreground = Color.WHITE
        btn.isFocusPainted = false
        btn.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT.darker(), 1),
            EmptyBorder(4, 12, 4, 12)
        )
        btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        return btn
    }

    private fun createLabel(text: String): JLabel {
        val l = JLabel(text)
        l.foreground = FG_TEXT
        l.font = Font("SansSerif", Font.PLAIN, 12)
        return l
    }

    private fun createSectionLabel(text: String): JLabel {
        val l = JLabel(text)
        l.foreground = ACCENT
        l.font = Font("SansSerif", Font.BOLD, 13)
        l.alignmentX = LEFT_ALIGNMENT
        return l
    }
}
