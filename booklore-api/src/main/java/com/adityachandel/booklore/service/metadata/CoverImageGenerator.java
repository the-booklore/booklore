package com.adityachandel.booklore.service.metadata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CoverImageGenerator {

    private static final int WIDTH = 250;
    private static final int HEIGHT = 350;
    private static final int PADDING = 20;
    private static final int TITLE_TOP_MARGIN = 40;
    private static final int TITLE_PADDING = 15;
    private static final int AUTHOR_BOTTOM_MARGIN = 330;
    private static final int MAX_LINES = 5;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    public byte[] generateCover(String title, String author) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Color[] colors = getColorScheme(title);
        Color bgColor1 = colors[0];
        Color bgColor2 = colors[1];

        GradientPaint backgroundGradient = new GradientPaint(
                0, 0, bgColor1,
                0, HEIGHT, bgColor2
        );
        g2d.setPaint(backgroundGradient);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        String effectiveTitle = title != null && !title.isBlank() ? title : "Unknown Title";
        String effectiveAuthor = author != null && !author.isBlank() ? author : "Unknown Author";

        Font titleFont = getTitleFont(effectiveTitle.length());
        g2d.setFont(titleFont);
        FontMetrics titleFm = g2d.getFontMetrics();
        
        List<String> titleLines = wrapText(effectiveTitle, titleFm);
        int titleLineHeight = titleFm.getHeight();
        int titleBoxHeight = (titleLines.size() * titleLineHeight) + (TITLE_PADDING * 2);
        
        GradientPaint titleBgGradient = new GradientPaint(
                0, TITLE_TOP_MARGIN, new Color(221, 221, 221), // #dddddd
                0, TITLE_TOP_MARGIN + titleBoxHeight, new Color(223, 223, 223, 153) // #dfdfdf with 0.6 opacity
        );
        g2d.setPaint(titleBgGradient);
        g2d.fillRect(0, TITLE_TOP_MARGIN, WIDTH, titleBoxHeight);

        g2d.setColor(Color.BLACK);
        int currentY = TITLE_TOP_MARGIN + TITLE_PADDING + titleFm.getAscent();
        for (String line : titleLines) {
            g2d.drawString(line, PADDING, currentY);
            currentY += titleLineHeight;
        }

        Font authorFont = getAuthorFont(effectiveAuthor.length());
        g2d.setFont(authorFont);
        FontMetrics authorFm = g2d.getFontMetrics();
        
        List<String> authorLines = wrapText(effectiveAuthor, authorFm);
        int authorLineHeight = authorFm.getHeight();
        int authorStartY = AUTHOR_BOTTOM_MARGIN - ((authorLines.size() - 1) * authorLineHeight);

        g2d.setColor(Color.BLACK);
        for (int i = 0; i < authorLines.size(); i++) {
            String line = authorLines.get(i);
            Rectangle2D rect = authorFm.getStringBounds(line, g2d);
            int x = WIDTH - PADDING - (int) rect.getWidth(); // Right align
            g2d.drawString(line, x, authorStartY + (i * authorLineHeight));
        }

        g2d.dispose();

        return writeHighQualityJpeg(image);
    }

    private byte[] writeHighQualityJpeg(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.95f); // High quality

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
                writer.dispose();
                return baos.toByteArray();
            }
        } catch (IOException e) {
            log.error("Failed to generate cover image", e);
            throw new RuntimeException("Failed to generate cover image", e);
        }
    }

    private Font getTitleFont(int length) {
        try {
            Font font = new Font("Georgia", Font.BOLD, getTitleFontSize(length));
            if (font.getFamily().equals("Georgia")) return font;
        } catch (Exception e) {
            log.warn("Failed to load preferred font, using fallback");
        }
        return new Font(Font.SERIF, Font.BOLD, getTitleFontSize(length));
    }

    private int getTitleFontSize(int length) {
        if (length < 20) return 36;
        if (length < 40) return 30;
        return 24;
    }

    private Font getAuthorFont(int length) {
        if (length < 20) return new Font(Font.SANS_SERIF, Font.PLAIN, 28);
        return new Font(Font.SANS_SERIF, Font.PLAIN, 22);
    }

    private static List<String> wrapText(String text, FontMetrics fm) {
        List<String> lines = new ArrayList<>();
        String[] words = WHITESPACE_PATTERN.split(text);
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.isEmpty()) {
                currentLine.append(word);
            } else {
                int currentWidth = fm.stringWidth(currentLine.toString());
                int spaceWidth = fm.stringWidth(" ");
                int wordWidth = fm.stringWidth(word);
                if (currentWidth + spaceWidth + wordWidth <= 210) {
                    currentLine.append(" ").append(word);
                } else {
                    lines.add(currentLine.toString().trim());
                    currentLine = new StringBuilder(word);
                }
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString().trim());
        }
        
        if (lines.size() > MAX_LINES) {
            List<String> truncated = lines.subList(0, MAX_LINES);
            String lastLine = truncated.get(MAX_LINES - 1);
            
            if (fm.stringWidth(lastLine + "...") > 210) {
                while (lastLine.length() > 3 && fm.stringWidth(lastLine + "...") > 210) {
                    lastLine = lastLine.substring(0, lastLine.length() - 1);
                }
            }
            truncated.set(MAX_LINES - 1, lastLine.trim() + "...");
            return truncated;
        }
        return lines;
    }

    private Color[] getColorScheme(String title) {
        int hash = Math.abs((title != null ? title : "").hashCode());
        Color[][] schemes = {
            {new Color(85, 119, 102), new Color(102, 153, 136)},   // Green (Original)
            {new Color(102, 119, 153), new Color(136, 153, 187)},  // Blue
            {new Color(153, 102, 119), new Color(187, 136, 153)},  // Purple
            {new Color(153, 153, 102), new Color(187, 187, 136)},  // Olive
            {new Color(102, 153, 153), new Color(136, 187, 187)},  // Teal
            {new Color(119, 102, 153), new Color(153, 136, 187)}   // Indigo
        };
        return schemes[hash % schemes.length];
    }
}