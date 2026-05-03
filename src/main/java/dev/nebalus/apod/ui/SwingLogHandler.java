package dev.nebalus.apod.ui;

import dev.nebalus.library.jlogger.LogLevel;
import dev.nebalus.library.jlogger.LogRecord;
import dev.nebalus.library.jlogger.handler.AbstractProcessingHandler;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class SwingLogHandler extends AbstractProcessingHandler {
    private final JTextArea textArea;

    public SwingLogHandler(JTextArea textArea, LogLevel level) {
        super(level, true);
        this.textArea = textArea;
    }

    public SwingLogHandler(JTextArea textArea) {
        this(textArea, LogLevel.DEBUG);
    }

    @Override
    public void write(LogRecord logRecord) {
        String line = (logRecord.formatted != null ? logRecord.formatted : logRecord.getMessage()) + "\n";
        SwingUtilities.invokeLater(() -> {
            textArea.append(line);
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }
}
