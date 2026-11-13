package com.example.watermark;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class WatermarkRenderer {
    // 120 DPI：文字字号使用 point；打印图片通过 VML 按 point 设置物理尺寸。
    public static final double PIXELS_PER_POINT = 120.0 / 72;

    public BufferedImage readLogo(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) return null;
        if (bytes.length > 5 * 1024 * 1024) throw new IllegalArgumentException("图片不能超过 5MB");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("请上传 PNG 或 JPEG 图片");
            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().matches("(?i)png|jpe?g")) throw new IllegalArgumentException("请上传 PNG 或 JPEG 图片");
                reader.setInput(input);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 16_000_000) {
                    throw new IllegalArgumentException("图片像素过大，请缩小到 1600 万像素以内");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    public byte[] render(WatermarkOptions options, BufferedImage logo, int width, int height) throws IOException {
        if (options.type() != WatermarkOptions.Type.TEXT && logo == null) {
            throw new IllegalArgumentException("图片水印需要上传 PNG 或 JPEG 图片");
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setComposite(AlphaComposite.SrcOver.derive((float) options.opacity()));
            graphics.setFont(new Font("Microsoft YaHei", Font.BOLD, (int) Math.round(options.fontSize() * PIXELS_PER_POINT)));
            graphics.setColor(Color.decode(options.color()));
            String[] lines = options.text().split("\\R", -1);
            FontMetrics metrics = graphics.getFontMetrics();
            int textWidth = 0;
            if (options.type() != WatermarkOptions.Type.IMAGE) {
                for (String line : lines) textWidth = Math.max(textWidth, metrics.stringWidth(line));
            }
            int logoWidth = 0;
            int logoHeight = 0;
            if (logo != null && options.type() != WatermarkOptions.Type.TEXT) {
                double scale = Math.min(options.fontSize() * 4.0 / logo.getWidth(), options.fontSize() * 4.0 / logo.getHeight());
                logoWidth = Math.max(1, (int) Math.round(logo.getWidth() * scale));
                logoHeight = Math.max(1, (int) Math.round(logo.getHeight() * scale));
            }
            int textHeight = options.type() == WatermarkOptions.Type.IMAGE ? 0 : metrics.getHeight() * lines.length;
            int contentHeight = textHeight + logoHeight + (textHeight > 0 && logoHeight > 0 ? 12 : 0);
            int contentWidth = Math.max(textWidth, logoWidth);
            double angle = Math.toRadians(options.angle());
            int boundsWidth = (int) (Math.abs(contentWidth * Math.cos(angle)) + Math.abs(contentHeight * Math.sin(angle)));
            int boundsHeight = (int) (Math.abs(contentWidth * Math.sin(angle)) + Math.abs(contentHeight * Math.cos(angle)));
            double gap = switch (options.density()) { case "sparse" -> 1.7; case "dense" -> 0.75; default -> 1.15; };
            int stepX = Math.max(240, (int) (boundsWidth + 110 * gap));
            int stepY = Math.max(190, (int) (boundsHeight + 90 * gap));
            if (options.layout().equals("single")) {
                drawStamp(graphics, options, logo, lines, width / 2.0, height / 2.0, logoWidth, logoHeight, contentHeight);
            } else {
                for (int y = 0, row = 0; y < height + stepY; y += stepY, row++) {
                    for (int x = row % 2 == 0 ? 0 : -stepX / 2; x < width + stepX; x += stepX) {
                        drawStamp(graphics, options, logo, lines, x, y, logoWidth, logoHeight, contentHeight);
                    }
                }
            }
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void drawStamp(Graphics2D graphics, WatermarkOptions options, BufferedImage logo, String[] lines,
                           double x, double y, int logoWidth, int logoHeight, int height) {
        AffineTransform previous = graphics.getTransform();
        graphics.translate(x, y);
        graphics.rotate(Math.toRadians(options.angle()));
        int top = -height / 2;
        if (logoWidth > 0) {
            graphics.drawImage(logo, -logoWidth / 2, top, logoWidth, logoHeight, null);
            top += logoHeight + 12;
        }
        if (options.type() != WatermarkOptions.Type.IMAGE) {
            FontMetrics metrics = graphics.getFontMetrics();
            for (String line : lines) {
                graphics.drawString(line, -metrics.stringWidth(line) / 2, top + metrics.getAscent());
                top += metrics.getHeight();
            }
        }
        graphics.setTransform(previous);
    }
}
