package dev.nebalus.apod.ui

import dev.nebalus.apod.APODEntry
import java.awt.*
import java.awt.image.BufferedImage
import java.io.InputStream
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.JPanel
import javax.swing.SwingWorker
import javax.swing.ToolTipManager

class ImagePanel : JPanel() {
    private var image: BufferedImage? = null
    private var loading = false
    private var errorMessage: String? = null
    private var metadata: APODEntry? = null

    init {
        background = Color(30, 30, 30)
        preferredSize = Dimension(800, 500)
        ToolTipManager.sharedInstance().initialDelay = 300
        ToolTipManager.sharedInstance().dismissDelay = 30_000
    }

    fun setMetadata(entry: APODEntry?) {
        metadata = entry
        toolTipText = if (entry != null) {
            val resolution = image?.let { "${it.width} × ${it.height}" } ?: "N/A"
            """<html><table style='padding:6px; font-family:SansSerif; font-size:11px;'>
                <tr><td><b>Title:</b></td><td>${entry.title}</td></tr>
                <tr><td><b>Date:</b></td><td>${entry.date}</td></tr>
                <tr><td><b>Type:</b></td><td>${entry.mediaType}</td></tr>
                <tr><td><b>URL:</b></td><td>${entry.url.take(60)}${if (entry.url.length > 60) "..." else ""}</td></tr>
                <tr><td><b>HD URL:</b></td><td>${(entry.hdurl ?: "N/A").take(60)}${if ((entry.hdurl?.length ?: 0) > 60) "..." else ""}</td></tr>
                <tr><td><b>Resolution:</b></td><td>$resolution</td></tr>
            </table></html>""".trimIndent()
        } else null
    }

    fun loadFromUrl(url: String) {
        loading = true
        errorMessage = null
        repaint()

        object : SwingWorker<BufferedImage?, Void>() {
            override fun doInBackground(): BufferedImage? {
                return try {
                    URL(url).openStream().use { input: InputStream ->
                        ImageIO.read(input)
                    }
                } catch (e: Exception) {
                    errorMessage = "Failed to load image: ${e.message}"
                    null
                }
            }

            override fun done() {
                loading = false
                image = get()
                repaint()
            }
        }.execute()
    }

    fun loadFromFile(file: java.io.File) {
        loading = true
        errorMessage = null
        repaint()

        object : SwingWorker<BufferedImage?, Void>() {
            override fun doInBackground(): BufferedImage? {
                return try {
                    ImageIO.read(file)
                } catch (e: Exception) {
                    errorMessage = "Failed to load image: ${e.message}"
                    null
                }
            }

            override fun done() {
                loading = false
                image = get()
                repaint()
            }
        }.execute()
    }

    fun clear() {
        image = null
        errorMessage = null
        loading = false
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        if (loading) {
            g2.color = Color(180, 180, 180)
            g2.font = Font("SansSerif", Font.PLAIN, 16)
            val msg = "Loading..."
            val fm = g2.fontMetrics
            g2.drawString(msg, (width - fm.stringWidth(msg)) / 2, height / 2)
            return
        }

        if (errorMessage != null) {
            g2.color = Color(200, 80, 80)
            g2.font = Font("SansSerif", Font.PLAIN, 14)
            val fm = g2.fontMetrics
            g2.drawString(errorMessage!!, (width - fm.stringWidth(errorMessage!!)) / 2, height / 2)
            return
        }

        val img = image ?: run {
            g2.color = Color(100, 100, 100)
            g2.font = Font("SansSerif", Font.ITALIC, 16)
            val msg = "No image loaded"
            val fm = g2.fontMetrics
            g2.drawString(msg, (width - fm.stringWidth(msg)) / 2, height / 2)
            return
        }

        val scale = minOf(width.toDouble() / img.width, height.toDouble() / img.height)
        val w = (img.width * scale).toInt()
        val h = (img.height * scale).toInt()
        val x = (width - w) / 2
        val y = (height - h) / 2
        g2.drawImage(img, x, y, w, h, null)
    }
}
