package dev.kweetn.sky;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * "Best" quality: copies a sky pack and
 *  - scales sky sheets (3x2 cube faces) that are smaller than 2048 px per face up to 2048 px per face,
 *  - adds smooth-filtering hints (.mcmeta) next to every sky image.
 * Everything else in the pack is copied untouched.
 */
final class BestQuality {
    static final int TARGET_FACE = 2048;
    static final String META = "{\"texture\": {\"blur\": true, \"clamp\": true}}";

    private BestQuality() {
    }

    static void process(InputStream in, OutputStream out) throws IOException {
        Set<String> written = new HashSet<>();
        try (ZipInputStream zin = new ZipInputStream(in); ZipOutputStream zout = new ZipOutputStream(out)) {
            ZipEntry en;
            while ((en = zin.getNextEntry()) != null) {
                if (en.isDirectory()) continue;
                String name = en.getName();
                byte[] data = zin.readAllBytes();
                String low = name.toLowerCase(Locale.ROOT);
                boolean sky = low.endsWith(".png") && (low.contains("optifine/sky/") || low.contains("mcpatcher/sky/"));
                if (sky) {
                    put(zout, written, name, upscaleSheet(data));
                    put(zout, written, name + ".mcmeta", META.getBytes(StandardCharsets.UTF_8));
                } else {
                    put(zout, written, name, data);
                }
            }
        }
    }

    private static void put(ZipOutputStream zout, Set<String> written, String name, byte[] data) throws IOException {
        if (!written.add(name)) return;
        zout.putNextEntry(new ZipEntry(name));
        zout.write(data);
        zout.closeEntry();
    }

    static byte[] upscaleSheet(byte[] data) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            if (src == null) return data;
            int w = src.getWidth();
            int h = src.getHeight();
            if (w % 3 != 0 || h % 2 != 0 || w / 3 != h / 2) return data; // not a 3x2 sheet of squares
            int tile = w / 3;
            if (tile >= TARGET_FACE) return data;                         // already big enough
            int t = TARGET_FACE;
            BufferedImage out = new BufferedImage(t * 3, t * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            for (int ty = 0; ty < 2; ty++) {
                for (int tx = 0; tx < 3; tx++) {
                    BufferedImage sub = src.getSubimage(tx * tile, ty * tile, tile, tile);
                    g.drawImage(sub, tx * t, ty * t, t, t, null);
                }
            }
            g.dispose();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bo);
            return bo.toByteArray();
        } catch (Throwable ex) {
            return data; // never break a sky because of the quality step
        }
    }
}
