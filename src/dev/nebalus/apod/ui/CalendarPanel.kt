package dev.nebalus.apod.ui

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*
import javax.swing.*
import javax.swing.border.EmptyBorder

class CalendarPanel(
    private val onRangeSelected: (LocalDate, LocalDate) -> Unit
) : JPanel() {

    companion object {
        private val BG_DARK = Color(30, 30, 30)
        private val BG_CELL = Color(50, 50, 50)
        private val BG_HOVER = Color(70, 70, 70)
        private val BG_SELECTED = Color(70, 130, 220)
        private val BG_RANGE = Color(50, 90, 160)
        private val BG_TODAY = Color(80, 160, 80)
        private val FG_TEXT = Color(220, 220, 220)
        private val FG_DIM = Color(100, 100, 100)
        private val ACCENT = Color(70, 130, 220)
    }

    private var currentMonth: YearMonth = YearMonth.now()
    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null
    private var hoverDate: LocalDate? = null

    private val monthLabel = JLabel("", SwingConstants.CENTER)
    private val gridPanel = JPanel()
    private val selectionLabel = JLabel("No selection", SwingConstants.CENTER)
    private val importBtn = JButton("Import Selected Range")

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = BG_DARK
        border = EmptyBorder(8, 8, 8, 8)

        // Header with month navigation
        val header = JPanel(BorderLayout(4, 0))
        header.background = BG_DARK
        header.maximumSize = Dimension(Int.MAX_VALUE, 36)

        val prevBtn = JButton("◄")
        prevBtn.background = ACCENT
        prevBtn.foreground = Color.WHITE
        prevBtn.isFocusPainted = false
        prevBtn.addActionListener {
            currentMonth = currentMonth.minusMonths(1)
            rebuildGrid()
        }

        val nextBtn = JButton("►")
        nextBtn.background = ACCENT
        nextBtn.foreground = Color.WHITE
        nextBtn.isFocusPainted = false
        nextBtn.addActionListener {
            currentMonth = currentMonth.plusMonths(1)
            rebuildGrid()
        }

        monthLabel.foreground = FG_TEXT
        monthLabel.font = Font("SansSerif", Font.BOLD, 14)

        header.add(prevBtn, BorderLayout.WEST)
        header.add(monthLabel, BorderLayout.CENTER)
        header.add(nextBtn, BorderLayout.EAST)
        add(header)
        add(Box.createVerticalStrut(6))

        // Day-of-week labels
        val dowPanel = JPanel(GridLayout(1, 7, 2, 0))
        dowPanel.background = BG_DARK
        dowPanel.maximumSize = Dimension(Int.MAX_VALUE, 20)
        for (dow in DayOfWeek.entries) {
            val label = JLabel(dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), SwingConstants.CENTER)
            label.foreground = FG_DIM
            label.font = Font("SansSerif", Font.PLAIN, 10)
            dowPanel.add(label)
        }
        add(dowPanel)
        add(Box.createVerticalStrut(4))

        // Calendar grid
        gridPanel.background = BG_DARK
        gridPanel.maximumSize = Dimension(Int.MAX_VALUE, 200)
        add(gridPanel)
        add(Box.createVerticalStrut(6))

        // Selection info
        selectionLabel.foreground = FG_TEXT
        selectionLabel.font = Font("SansSerif", Font.PLAIN, 11)
        selectionLabel.alignmentX = LEFT_ALIGNMENT
        add(selectionLabel)
        add(Box.createVerticalStrut(6))

        // Import button
        importBtn.background = ACCENT
        importBtn.foreground = Color.WHITE
        importBtn.isFocusPainted = false
        importBtn.alignmentX = LEFT_ALIGNMENT
        importBtn.isEnabled = false
        importBtn.addActionListener { triggerImport() }
        add(importBtn)

        rebuildGrid()
    }

    fun clearSelection() {
        startDate = null
        endDate = null
        updateSelectionLabel()
        rebuildGrid()
    }

    private fun rebuildGrid() {
        gridPanel.removeAll()
        gridPanel.layout = GridLayout(0, 7, 2, 2)

        monthLabel.text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${currentMonth.year}"

        val firstDay = currentMonth.atDay(1)
        val startPad = (firstDay.dayOfWeek.value - 1) // Monday = 0

        // Padding for days before month start
        for (i in 0 until startPad) {
            gridPanel.add(createEmptyCell())
        }

        val today = LocalDate.now()
        for (day in 1..currentMonth.lengthOfMonth()) {
            val date = currentMonth.atDay(day)
            val cell = createDayCell(date, date == today)
            gridPanel.add(cell)
        }

        gridPanel.revalidate()
        gridPanel.repaint()
    }

    private fun createEmptyCell(): JLabel {
        val l = JLabel("")
        l.isOpaque = true
        l.background = BG_DARK
        return l
    }

    private fun createDayCell(date: LocalDate, isToday: Boolean): JLabel {
        val label = JLabel(date.dayOfMonth.toString(), SwingConstants.CENTER)
        label.isOpaque = true
        label.font = Font("SansSerif", Font.PLAIN, 12)
        label.foreground = FG_TEXT
        label.preferredSize = Dimension(36, 28)
        label.border = BorderFactory.createLineBorder(Color(40, 40, 40), 1)
        label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        // Color logic
        updateCellColor(label, date, isToday)

        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onDateClicked(date)
                rebuildGrid()
            }
            override fun mouseEntered(e: MouseEvent) {
                hoverDate = date
                label.background = BG_HOVER
            }
            override fun mouseExited(e: MouseEvent) {
                hoverDate = null
                updateCellColor(label, date, isToday)
            }
        })

        return label
    }

    private fun updateCellColor(label: JLabel, date: LocalDate, isToday: Boolean) {
        val s = startDate
        val e = endDate

        label.background = when {
            date == s || date == e -> BG_SELECTED
            s != null && e != null && date.isAfter(s) && date.isBefore(e) -> BG_RANGE
            isToday -> BG_TODAY
            else -> BG_CELL
        }

        label.font = if (date == s || date == e) {
            Font("SansSerif", Font.BOLD, 12)
        } else {
            Font("SansSerif", Font.PLAIN, 12)
        }
    }

    private fun onDateClicked(date: LocalDate) {
        when {
            startDate == null -> {
                startDate = date
                endDate = null
            }
            endDate == null -> {
                if (date.isBefore(startDate)) {
                    endDate = startDate
                    startDate = date
                } else {
                    endDate = date
                }
            }
            else -> {
                startDate = date
                endDate = null
            }
        }
        updateSelectionLabel()
    }

    private fun updateSelectionLabel() {
        val s = startDate
        val e = endDate
        when {
            s == null -> {
                selectionLabel.text = "Click a start date"
                importBtn.isEnabled = false
            }
            e == null -> {
                selectionLabel.text = "Start: $s — click end date"
                importBtn.isEnabled = false
            }
            else -> {
                val days = java.time.temporal.ChronoUnit.DAYS.between(s, e) + 1
                selectionLabel.text = "$s → $e ($days days)"
                importBtn.isEnabled = true
            }
        }
    }

    private fun triggerImport() {
        val s = startDate ?: return
        val e = endDate ?: return
        onRangeSelected(s, e)
    }
}
