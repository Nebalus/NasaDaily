package dev.nebalus.apod.ui;

import dev.nebalus.apod.repository.CacheRepository;
import dev.nebalus.apod.Client;
import dev.nebalus.apod.Entry;
import dev.nebalus.apod.Importer;
import dev.nebalus.library.jlogger.LogLevel;
import dev.nebalus.library.jlogger.Logger;
import dev.nebalus.library.jlogger.formatter.LineFormatter;
import dev.nebalus.library.jlogger.handler.SyslogHandler;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class FetcherUI extends JFrame {
    private static final String API_KEY = "DEMO_KEY";
    private static final Path OUTPUT_DIR = Paths.get("apod_images");
    private static final Path CACHE_DIR = Paths.get(".apod_cache");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Color BG_DARK = new Color(30, 30, 30);
    private static final Color BG_PANEL = new Color(45, 45, 45);
    private static final Color BG_INPUT = new Color(60, 60, 60);
    private static final Color FG_TEXT = new Color(220, 220, 220);
    private static final Color ACCENT = new Color(70, 130, 220);

    private final Logger logger = new Logger("APODFetcher");
    private final CacheRepository cache = new CacheRepository(CACHE_DIR, logger);
    private final Client client = new Client(API_KEY, logger, cache);
    private final Importer importer = new Importer(OUTPUT_DIR, logger);

    private final ImagePanel imagePanel = new ImagePanel();
    private final JLabel titleLabel = new JLabel("No APOD loaded");
    private final JTextArea explanationArea = new JTextArea(3, 40);
    private final JLabel countLabel = new JLabel("");
    private final JSpinner rateLimitSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 300, 5));
    private final JTextArea logArea = new JTextArea(8, 60);
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel statusLabel = new JLabel("Ready");
    private final CalendarPanel calendar = new CalendarPanel((from, to) -> startRangeImport(from, to));

    private List<File> localFiles = new ArrayList<>();
    private int currentIndex = -1;

    public FetcherUI() {
        super("APODFetcher");
        setupDarkTheme();
        setupLogger();
        buildUI();
        setupWindow();
        refreshLocalFiles();
    }

    private void setupDarkTheme() {
        UIManager.put("Panel.background", BG_DARK);
        UIManager.put("Label.foreground", FG_TEXT);
        UIManager.put("TextField.background", BG_INPUT);
        UIManager.put("TextField.foreground", FG_TEXT);
        UIManager.put("TextField.caretForeground", FG_TEXT);
        UIManager.put("TextArea.background", BG_INPUT);
        UIManager.put("TextArea.foreground", FG_TEXT);
        UIManager.put("TextArea.caretForeground", FG_TEXT);
        UIManager.put("Button.background", ACCENT);
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("ProgressBar.background", BG_INPUT);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ScrollPane.background", BG_DARK);
        UIManager.put("Spinner.background", BG_INPUT);
        UIManager.put("Spinner.foreground", FG_TEXT);
    }

    private void setupLogger() {
        SyslogHandler consoleHandler = new SyslogHandler(LogLevel.DEBUG, true);
        logger.pushHandler(consoleHandler);

        SwingLogHandler uiHandler = new SwingLogHandler(logArea, LogLevel.DEBUG);
        uiHandler.setFormatter(new LineFormatter(null, "HH:mm:ss"));
        logger.pushHandler(uiHandler);
    }

    private void buildUI() {
        getContentPane().setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG_DARK);

        getContentPane().add(imagePanel, BorderLayout.CENTER);
        getContentPane().add(buildSidePanel(), BorderLayout.EAST);
        getContentPane().add(buildBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(BG_PANEL);
        side.setBorder(new EmptyBorder(12, 12, 12, 12));
        side.setPreferredSize(new Dimension(340, 0));

        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        titleLabel.setForeground(FG_TEXT);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(titleLabel);
        side.add(Box.createVerticalStrut(8));

        explanationArea.setEditable(false);
        explanationArea.setLineWrap(true);
        explanationArea.setWrapStyleWord(true);
        explanationArea.setBackground(BG_PANEL);
        explanationArea.setForeground(new Color(180, 180, 180));
        explanationArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        explanationArea.setBorder(null);
        JScrollPane explScroll = new JScrollPane(explanationArea);
        explScroll.setBorder(null);
        explScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        explScroll.setPreferredSize(new Dimension(320, 120));
        side.add(explScroll);
        side.add(Box.createVerticalStrut(16));

        side.add(createSectionLabel("Browse (Local)"));
        side.add(Box.createVerticalStrut(4));

        JPanel dateNav = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dateNav.setBackground(BG_PANEL);
        dateNav.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton prevBtn = createButton("◄");
        prevBtn.addActionListener(e -> navigateLocal(-1));
        dateNav.add(prevBtn);

        countLabel.setForeground(FG_TEXT);
        countLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        dateNav.add(countLabel);

        JButton nextBtn = createButton("►");
        nextBtn.addActionListener(e -> navigateLocal(1));
        dateNav.add(nextBtn);

        JButton refreshBtn = createButton("Refresh");
        refreshBtn.addActionListener(e -> refreshLocalFiles());
        dateNav.add(refreshBtn);

        side.add(dateNav);

        side.add(Box.createVerticalStrut(16));

        side.add(createSectionLabel("Import (Calendar)"));
        side.add(Box.createVerticalStrut(4));

        calendar.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(calendar);

        side.add(Box.createVerticalStrut(8));

        JPanel ratePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ratePanel.setBackground(BG_PANEL);
        ratePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        ratePanel.add(createLabel("Rate limit (s):"));
        ratePanel.add(rateLimitSpinner);
        side.add(ratePanel);

        side.add(Box.createVerticalStrut(8));

        JButton importTodayBtn = createButton("Import Today");
        importTodayBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        importTodayBtn.addActionListener(e -> startTodayImport());
        side.add(importTodayBtn);

        side.add(Box.createVerticalGlue());
        return side;
    }

    private JPanel buildBottomPanel() {
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setBackground(BG_PANEL);
        bottom.setBorder(new EmptyBorder(8, 12, 8, 12));

        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBackground(new Color(25, 25, 25));
        logArea.setForeground(new Color(160, 220, 160));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(0, 150));
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60)));

        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setBackground(BG_PANEL);
        logHeader.add(createSectionLabel("Log"), BorderLayout.WEST);
        JButton clearBtn = createButton("Clear");
        clearBtn.addActionListener(e -> logArea.setText(""));
        logHeader.add(clearBtn, BorderLayout.EAST);

        bottom.add(logHeader, BorderLayout.NORTH);
        bottom.add(logScroll, BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBackground(BG_PANEL);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(200, 20));
        progressBar.setBackground(BG_INPUT);
        progressBar.setForeground(ACCENT);
        statusLabel.setForeground(new Color(150, 150, 150));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        statusBar.add(progressBar, BorderLayout.WEST);
        statusBar.add(statusLabel, BorderLayout.CENTER);
        bottom.add(statusBar, BorderLayout.SOUTH);

        return bottom;
    }

    private void setupWindow() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                logger.close();
                dispose();
                System.exit(0);
            }
        });
        setMinimumSize(new Dimension(1100, 700));
        setSize(1200, 800);
        setLocationRelativeTo(null);
    }

    private void refreshLocalFiles() {
        localFiles = scanLocalImages();
        localFiles.sort(Comparator.comparing(File::getName));
        
        Set<LocalDate> dates = new HashSet<>();
        for (File f : localFiles) {
            String name = f.getName();
            if (name.length() >= 10) {
                try {
                    dates.add(LocalDate.parse(name.substring(0, 10), DATE_FMT));
                } catch (Exception ignored) {}
            }
        }
        calendar.setImportedDates(dates);
        
        if (localFiles.isEmpty()) {
            currentIndex = -1;
            imagePanel.clear();
            titleLabel.setText("No images found");
            explanationArea.setText("Use the Import section to download APODs first.");
            countLabel.setText("0 / 0");
            statusLabel.setText("No local images");
        } else {
            currentIndex = localFiles.size() - 1; // show latest
            showLocalImage(currentIndex);
        }
        logger.info(String.format("Found %d local images", localFiles.size()));
    }

    private List<File> scanLocalImages() {
        List<File> files = new ArrayList<>();
        scanDir(OUTPUT_DIR.toFile(), files);
        return files;
    }

    private void scanDir(File dir, List<File> files) {
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child.isDirectory()) {
                scanDir(child, files);
            } else {
                String name = child.getName();
                if (!name.endsWith(".url") && !name.endsWith(".tmp")) {
                    files.add(child);
                }
            }
        }
    }

    private void showLocalImage(int index) {
        if (index < 0 || index >= localFiles.size()) return;

        File file = localFiles.get(index);
        countLabel.setText((index + 1) + " / " + localFiles.size());

        String name = file.getName();
        String dateStr = name.length() >= 10 ? name.substring(0, 10) : "";
        
        Entry cachedEntry = null;
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FMT);
            cachedEntry = cache.get(date);
        } catch (Exception ignored) {}

        if (cachedEntry != null) {
            titleLabel.setText("<html><font color='#aaaaaa' size='-1'>" + cachedEntry.date() + "</font><br><b>" + cachedEntry.title() + "</b></html>");
            explanationArea.setText(cachedEntry.explanation());
            explanationArea.setCaretPosition(0);
        } else {
            String tempName = name;
            int dotIndex = tempName.lastIndexOf('.');
            if (dotIndex > 0) tempName = tempName.substring(0, dotIndex);
            
            if (tempName.startsWith(dateStr)) {
                tempName = tempName.substring(dateStr.length());
            }
            if (tempName.startsWith("_")) {
                tempName = tempName.substring(1);
            }
            tempName = tempName.replace("_", " ");
            
            titleLabel.setText("<html><b>" + tempName + "</b></html>");
            explanationArea.setText("");
        }

        imagePanel.loadFromFile(file);
        imagePanel.setMetadata(cachedEntry);
        statusLabel.setText(file.getName());
    }

    private void navigateLocal(int offset) {
        if (localFiles.isEmpty()) return;
        currentIndex = currentIndex + offset;
        if (currentIndex < 0) currentIndex = 0;
        if (currentIndex >= localFiles.size()) currentIndex = localFiles.size() - 1;
        showLocalImage(currentIndex);
    }

    private void startTodayImport() {
        statusLabel.setText("Importing today's APOD...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    Entry entry = client.fetchToday();
                    importer.importEntry(entry);
                } catch (Exception e) {
                    logger.error(e);
                }
                return null;
            }

            @Override
            protected void done() {
                refreshLocalFiles();
                statusLabel.setText("Ready");
            }
        }.execute();
    }

    private void startRangeImport(LocalDate from, LocalDate to) {
        long rate = ((Number) rateLimitSpinner.getValue()).longValue();
        Importer rateLimitedImporter = new Importer(OUTPUT_DIR, logger, rate);
        statusLabel.setText("Importing " + from + " to " + to + "...");

        new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() {
                try {
                    List<Entry> entries = client.fetchRange(from, to);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setMaximum(entries.size());
                        progressBar.setValue(0);
                    });
                    rateLimitedImporter.importEntries(entries);
                } catch (Exception e) {
                    logger.error(e);
                }
                return null;
            }

            @Override
            protected void done() {
                progressBar.setValue(progressBar.getMaximum());
                statusLabel.setText("Import complete");
                refreshLocalFiles();
            }
        }.execute();
    }

    private JButton createButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(ACCENT);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT.darker(), 1),
            new EmptyBorder(4, 12, 4, 12)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(FG_TEXT);
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        return l;
    }

    private JLabel createSectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(ACCENT);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }
}
