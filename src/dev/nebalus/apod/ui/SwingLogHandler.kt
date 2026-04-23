package dev.nebalus.apod.ui

import dev.nebalus.library.jlogger.LogLevel
import dev.nebalus.library.jlogger.LogRecord
import dev.nebalus.library.jlogger.handler.AbstractProcessingHandler
import javax.swing.JTextArea
import javax.swing.SwingUtilities

class SwingLogHandler(
    private val textArea: JTextArea,
    level: LogLevel = LogLevel.DEBUG
) : AbstractProcessingHandler(level, true) {

    override fun write(logRecord: LogRecord) {
        val line = (logRecord.formatted ?: logRecord.message) + "\n"
        SwingUtilities.invokeLater {
            textArea.append(line)
            textArea.caretPosition = textArea.document.length
        }
    }
}
