package dev.nebalus.nasadaily.ui;

import dev.nebalus.nasadaily.Entry;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;

public class ImagePanel extends JPanel {
    private BufferedImage image = null;
    private boolean loading = false;
    private String errorMessage = null;
    private Entry metadata = null;

    public ImagePanel() {
        setBackground(new Color(30, 30, 30));
        setPreferredSize(new Dimension(800, 500));
        ToolTipManager.sharedInstance().setInitialDelay(300);
        ToolTipManager.sharedInstance().setDismissDelay(30_000);
    }

    public void setMetadata(Entry entry) {
        this.metadata = entry;
        if (entry != null) {
            String resolution = image != null ? image.getWidth() + " × " + image.getHeight() : "N/A";
            String urlDisplay = entry.url().length() > 60 ? entry.url().substring(0, 60) + "..." : entry.url();
            String hdUrl = entry.hdurl() != null ? entry.hdurl() : "N/A";
            String hdUrlDisplay = hdUrl.length() > 60 ? hdUrl.substring(0, 60) + "..." : hdUrl;

            setToolTipText(
                "<html><table style='padding:6px; font-family:SansSerif; font-size:11px;'>" +
                "<tr><td><b>Title:</b></td><td>" + entry.title() + "</td></tr>" +
                "<tr><td><b>Date:</b></td><td>" + entry.date() + "</td></tr>" +
                "<tr><td><b>Type:</b></td><td>" + entry.mediaType() + "</td></tr>" +
                "<tr><td><b>URL:</b></td><td>" + urlDisplay + "</td></tr>" +
                "<tr><td><b>HD URL:</b></td><td>" + hdUrlDisplay + "</td></tr>" +
                "<tr><td><b>Resolution:</b></td><td>" + resolution + "</td></tr>" +
                "</table></html>"
            );
        } else {
            setToolTipText(null);
        }
    }

    public void loadFromUrl(String urlString) {
        loading = true;
        errorMessage = null;
        repaint();

        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                try {
                    @SuppressWarnings("deprecation")
                    URL url = new URL(urlString);
                    try (InputStream input = url.openStream()) {
                        return ImageIO.read(input);
                    }
                } catch (Exception e) {
                    errorMessage = "Failed to load image: " + e.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                loading = false;
                try {
                    image = get();
                } catch (Exception e) {
                    image = null;
                }
                repaint();
            }
        }.execute();
    }

    public void loadFromFile(File file) {
        loading = true;
        errorMessage = null;
        repaint();

        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                try {
                    return ImageIO.read(file);
                } catch (Exception e) {
                    errorMessage = "Failed to load image: " + e.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                loading = false;
                try {
                    image = get();
                } catch (Exception e) {
                    image = null;
                }
                repaint();
            }
        }.execute();
    }

    public void clear() {
        image = null;
        errorMessage = null;
        loading = false;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (loading) {
            g2.setColor(new Color(180, 180, 180));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
            String msg = "Loading...";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
            return;
        }

        if (errorMessage != null) {
            g2.setColor(new Color(200, 80, 80));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(errorMessage, (getWidth() - fm.stringWidth(errorMessage)) / 2, getHeight() / 2);
            return;
        }

        if (image == null) {
            g2.setColor(new Color(100, 100, 100));
            g2.setFont(new Font("SansSerif", Font.ITALIC, 16));
            String msg = "No image loaded";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
            return;
        }

        double scale = Math.min((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
        int w = (int) (image.getWidth() * scale);
        int h = (int) (image.getHeight() * scale);
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;
        g2.drawImage(image, x, y, w, h, null);
    }
}
