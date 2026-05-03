package dev.nebalus.nasadaily.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.Set;
import java.util.HashSet;

public class CalendarPanel extends JPanel {
    private static final Color BG_DARK = new Color(30, 30, 30);
    private static final Color BG_CELL = new Color(50, 50, 50);
    private static final Color BG_HOVER = new Color(70, 70, 70);
    private static final Color BG_SELECTED = new Color(70, 130, 220);
    private static final Color BG_RANGE = new Color(50, 90, 160);
    private static final Color BG_IMPORTED = new Color(80, 160, 80);
    private static final Color BG_MISSING = new Color(180, 60, 60);
    private static final Color FG_TEXT = new Color(220, 220, 220);
    private static final Color FG_DIM = new Color(100, 100, 100);
    private static final Color ACCENT = new Color(70, 130, 220);

    private final BiConsumer<LocalDate, LocalDate> onRangeSelected;

    private YearMonth currentMonth = YearMonth.now();
    private LocalDate startDate = null;
    private LocalDate endDate = null;
    @SuppressWarnings("unused")
    private LocalDate hoverDate = null;
    private Set<LocalDate> importedDates = new HashSet<>();

    private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
    private final JPanel gridPanel = new JPanel();
    private final JLabel selectionLabel = new JLabel("No selection", SwingConstants.LEFT);
    private final JButton importBtn = new JButton("Import Selected Range");

    public CalendarPanel(BiConsumer<LocalDate, LocalDate> onRangeSelected) {
        this.onRangeSelected = onRangeSelected;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BG_DARK);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        // Header with month navigation
        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.setBackground(BG_DARK);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JButton prevBtn = new JButton("◄");
        prevBtn.setBackground(ACCENT);
        prevBtn.setForeground(Color.WHITE);
        prevBtn.setFocusPainted(false);
        prevBtn.addActionListener(e -> {
            currentMonth = currentMonth.minusMonths(1);
            rebuildGrid();
        });

        JButton nextBtn = new JButton("►");
        nextBtn.setBackground(ACCENT);
        nextBtn.setForeground(Color.WHITE);
        nextBtn.setFocusPainted(false);
        nextBtn.addActionListener(e -> {
            currentMonth = currentMonth.plusMonths(1);
            rebuildGrid();
        });

        monthLabel.setForeground(FG_TEXT);
        monthLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        header.add(prevBtn, BorderLayout.WEST);
        header.add(monthLabel, BorderLayout.CENTER);
        header.add(nextBtn, BorderLayout.EAST);
        add(header);
        add(Box.createVerticalStrut(6));

        // Day-of-week labels
        JPanel dowPanel = new JPanel(new GridLayout(1, 7, 2, 0));
        dowPanel.setBackground(BG_DARK);
        dowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        for (DayOfWeek dow : DayOfWeek.values()) {
            JLabel label = new JLabel(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), SwingConstants.CENTER);
            label.setForeground(FG_DIM);
            label.setFont(new Font("SansSerif", Font.PLAIN, 10));
            dowPanel.add(label);
        }
        add(dowPanel);
        add(Box.createVerticalStrut(4));

        // Calendar grid
        gridPanel.setBackground(BG_DARK);
        gridPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        add(gridPanel);
        add(Box.createVerticalStrut(6));

        // Selection info
        selectionLabel.setForeground(FG_TEXT);
        selectionLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        selectionLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(selectionLabel);
        add(Box.createVerticalStrut(6));

        // Import button
        importBtn.setBackground(ACCENT);
        importBtn.setForeground(Color.WHITE);
        importBtn.setFocusPainted(false);
        importBtn.setAlignmentX(LEFT_ALIGNMENT);
        importBtn.setEnabled(false);
        importBtn.addActionListener(e -> triggerImport());
        add(importBtn);

        rebuildGrid();
    }

    public void setImportedDates(Set<LocalDate> importedDates) {
        this.importedDates = importedDates;
        rebuildGrid();
    }

    public void clearSelection() {
        startDate = null;
        endDate = null;
        updateSelectionLabel();
        rebuildGrid();
    }

    private void rebuildGrid() {
        gridPanel.removeAll();
        gridPanel.setLayout(new GridLayout(0, 7, 2, 2));

        monthLabel.setText(currentMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + currentMonth.getYear());

        LocalDate firstDay = currentMonth.atDay(1);
        int startPad = firstDay.getDayOfWeek().getValue() - 1; // Monday = 0

        for (int i = 0; i < startPad; i++) {
            gridPanel.add(createEmptyCell());
        }

        LocalDate today = LocalDate.now();
        for (int day = 1; day <= currentMonth.lengthOfMonth(); day++) {
            LocalDate date = currentMonth.atDay(day);
            JLabel cell = createDayCell(date, date.equals(today));
            gridPanel.add(cell);
        }

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private JLabel createEmptyCell() {
        JLabel l = new JLabel("");
        l.setOpaque(true);
        l.setBackground(BG_DARK);
        return l;
    }

    private JLabel createDayCell(LocalDate date, boolean isToday) {
        JLabel label = new JLabel(String.valueOf(date.getDayOfMonth()), SwingConstants.CENTER);
        label.setOpaque(true);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(FG_TEXT);
        label.setPreferredSize(new Dimension(36, 28));
        label.setBorder(BorderFactory.createLineBorder(new Color(40, 40, 40), 1));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        updateCellColor(label, date, isToday);

        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onDateClicked(date);
                rebuildGrid();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hoverDate = date;
                label.setBackground(BG_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverDate = null;
                updateCellColor(label, date, isToday);
            }
        });

        return label;
    }

    private void updateCellColor(JLabel label, LocalDate date, boolean isToday) {
        label.setBorder(isToday ? BorderFactory.createLineBorder(Color.WHITE, 2) : BorderFactory.createLineBorder(new Color(40, 40, 40), 1));
        
        if (date.equals(startDate) || date.equals(endDate)) {
            label.setBackground(BG_SELECTED);
            label.setFont(new Font("SansSerif", Font.BOLD, 12));
        } else if (startDate != null && endDate != null && date.isAfter(startDate) && date.isBefore(endDate)) {
            label.setBackground(BG_RANGE);
            label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        } else if (importedDates.contains(date)) {
            label.setBackground(BG_IMPORTED);
            label.setFont(new Font("SansSerif", isToday ? Font.BOLD : Font.PLAIN, 12));
        } else if (!date.isAfter(LocalDate.now())) {
            label.setBackground(BG_MISSING);
            label.setFont(new Font("SansSerif", isToday ? Font.BOLD : Font.PLAIN, 12));
        } else {
            label.setBackground(BG_CELL);
            label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        }
    }

    private void onDateClicked(LocalDate date) {
        if (startDate == null) {
            startDate = date;
            endDate = null;
        } else if (endDate == null) {
            if (date.isBefore(startDate)) {
                endDate = startDate;
                startDate = date;
            } else {
                endDate = date;
            }
        } else {
            startDate = date;
            endDate = null;
        }
        updateSelectionLabel();
    }

    private void updateSelectionLabel() {
        if (startDate == null) {
            selectionLabel.setText("Click a start date");
            importBtn.setEnabled(false);
        } else if (endDate == null) {
            selectionLabel.setText("Start: " + startDate + " — click end date");
            importBtn.setEnabled(false);
        } else {
            long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            selectionLabel.setText(startDate + " → " + endDate + " (" + days + " days)");
            importBtn.setEnabled(true);
        }
    }

    private void triggerImport() {
        if (startDate == null || endDate == null) return;
        onRangeSelected.accept(startDate, endDate);
    }
}
