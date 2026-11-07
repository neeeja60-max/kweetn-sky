package dev.kweetn.sky;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The Kweetn Sky menu: rounded blue window in the middle of the screen with tabs on the left. */
public class KweetnScreen extends Screen {
    // ---- blue theme (ARGB)
    private static final int PANEL = 0xFF0A1530;
    private static final int PANEL_BORDER = 0xFF2250B8;
    private static final int SIDEBAR = 0xFF0D1C3F;
    private static final int CARD = 0xFF12234A;
    private static final int CARD_HOVER = 0xFF1A3266;
    private static final int CARD_BORDER = 0xFF1C3166;
    private static final int CARD_BORDER_HOVER = 0xFF2F55A8;
    private static final int ACCENT = 0xFF3B82F6;
    private static final int ACCENT_SOFT = 0xFF7DB2FF;
    private static final int TEXT = 0xFFE8F0FF;
    private static final int MUTED = 0xFF8DA6D4;
    private static final int YELLOW = 0xFFFFD43B;
    private static final int WARN = 0xFFFFC857;
    private static final int DANGER = 0xFFC0392B;

    private enum Tab {
        SKY("Sky"), CUSTOM("Kweetn"), QUALITY("Quality");
        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Screen parent;
    private Tab tab = Tab.SKY;
    private int px, py, pw, ph;       // window
    private int sbW = 100;            // sidebar width
    private int cx, cy, cw, ch;       // content area
    private double scroll = 0;
    private int contentHeight = 0;
    private final List<Hit> hits = new ArrayList<>();
    private int dotTick = 0;

    public KweetnScreen(Screen parent) {
        super(Component.literal("Kweetn Sky"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(500, this.width - 24);
        ph = Math.min(320, this.height - 24);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        cx = px + sbW + 18;
        cy = py + 36;
        cw = pw - sbW - 28;
        ch = ph - 36 - 28;
        SkyManager.I.refreshCustom();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // =========================================================== drawing helpers
    /** Filled rounded rectangle made of horizontal strips. */
    private static void rr(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - d * d));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    private void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(this.font, s, x, y, color, false);
    }

    private void ctext(GuiGraphics g, String s, int centerX, int y, int color) {
        g.drawString(this.font, s, centerX - this.font.width(s) / 2, y, color, false);
    }

    private String trim(String s, int maxW) {
        if (this.font.width(s) <= maxW) return s;
        while (s.length() > 1 && this.font.width(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private List<String> wrap(String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(t) > maxW && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(t);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private boolean inContent(int mx, int my) {
        return mx >= cx && mx < cx + cw && my >= cy && my < cy + ch;
    }

    private boolean hoverContent(int mx, int my, int x, int y, int w, int h) {
        return inContent(mx, my) && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void addContentHit(int x, int y, int w, int h, Runnable r) {
        int x1 = Math.max(x, cx), y1 = Math.max(y, cy);
        int x2 = Math.min(x + w, cx + cw), y2 = Math.min(y + h, cy + ch);
        if (x2 > x1 && y2 > y1) hits.add(new Hit(x1, y1, x2 - x1, y2 - y1, r));
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String label, int color, Runnable r) {
        boolean hov = hoverContent(mx, my, x, y, w, h);
        rr(g, x, y, w, h, 5, hov ? lighten(color) : color);
        ctext(g, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        addContentHit(x, y, w, h, r);
    }

    private static int lighten(int c) {
        int a = c & 0xFF000000;
        int r = Math.min(255, ((c >> 16) & 0xFF) + 28);
        int gr = Math.min(255, ((c >> 8) & 0xFF) + 28);
        int b = Math.min(255, (c & 0xFF) + 28);
        return a | (r << 16) | (gr << 8) | b;
    }

    private void drawIcon(GuiGraphics g, SkyEntry e, int x, int y, int w, int h) {
        SkyManager.Icon ic = SkyManager.I.icon(e);
        if (ic == null) {
            g.fillGradient(x, y, x + w, y + h, 0xFF2A4C9A, 0xFF0B1630);
            ctext(g, e.name().substring(0, 1).toUpperCase(), x + w / 2, y + h / 2 - 4, TEXT);
            return;
        }
        // The pack icon is square; the card is a little wider than tall, so show the middle band.
        int regionH = Math.min(ic.h(), Math.round(ic.w() * (float) h / (float) w));
        int v = (ic.h() - regionH) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, ic.id(), x, y, 0, v, w, h, ic.w(), regionH, ic.w(), ic.h());
    }

    // =========================================================== main render
    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);      // vanilla background (blur) - we only draw on top of it
        hits.clear();
        dotTick++;

        g.fill(0, 0, this.width, this.height, 0x77030A18);

        // window: soft shadow, border, body
        rr(g, px - 3, py - 3, pw + 6, ph + 6, 13, 0x33000000);
        rr(g, px, py, pw, ph, 11, PANEL_BORDER);
        rr(g, px + 1, py + 1, pw - 2, ph - 2, 10, PANEL);

        // diagonal KWEETN watermark (clipped to the window)
        g.enableScissor(px + 1, py + 1, px + pw - 1, py + ph - 1);
        watermark(g);
        g.disableScissor();

        drawSidebar(g, mx, my);

        // title of the page
        String title = tab == Tab.SKY ? "Sky" : tab == Tab.CUSTOM ? "CUSTOM" : "Quality";
        g.drawString(this.font, title, cx, py + 13, 0xFFFFFFFF, true);
        g.fill(cx, py + 26, cx + 26, py + 28, ACCENT);

        // close button
        int cxb = px + pw - 22, cyb = py + 9;
        boolean hovClose = mx >= cxb && mx < cxb + 14 && my >= cyb && my < cyb + 14;
        rr(g, cxb, cyb, 14, 14, 5, hovClose ? 0xFFD64545 : 0xFF1A2B57);
        ctext(g, "x", cxb + 7, cyb + 3, 0xFFFFFFFF);
        hits.add(new Hit(cxb, cyb, 14, 14, this::onClose));

        // scrolling content
        g.enableScissor(cx, cy, cx + cw, cy + ch);
        int base = cy - (int) scroll;
        switch (tab) {
            case SKY -> contentHeight = drawSkyTab(g, mx, my, base);
            case CUSTOM -> contentHeight = drawCustomTab(g, mx, my, base);
            case QUALITY -> contentHeight = drawQualityTab(g, mx, my, base);
        }
        g.disableScissor();

        double max = Math.max(0, contentHeight - ch + 4);
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
        if (max > 0) { // slim scroll bar
            int barH = Math.max(18, (int) (ch * (ch / (double) (contentHeight + 4))));
            int barY = cy + (int) ((ch - barH) * (scroll / max));
            rr(g, cx + cw + 3, barY, 3, barH, 1, 0xFF2F55A8);
        }

        // status line
        String st = SkyManager.I.status;
        if (st != null && !st.isEmpty()) {
            String dots = SkyManager.I.busy ? ".".repeat((dotTick / 10) % 4) : "";
            text(g, trim(st, cw) + dots, cx, py + ph - 18, SkyManager.I.busy ? ACCENT_SOFT : MUTED);
        }
    }

    private void watermark(GuiGraphics g) {
        for (int i = -1; i <= 1; i++) {
            var m = g.pose();
            m.pushMatrix();
            m.translate(px + pw / 2f, py + ph / 2f + i * 78f);
            m.rotate((float) Math.toRadians(-24));
            m.scale(5f, 5f);
            String t = "KWEETN";
            g.drawString(this.font, t, -this.font.width(t) / 2, -4, 0x15FFFFFF, false);
            m.popMatrix();
        }
    }

    private void drawSidebar(GuiGraphics g, int mx, int my) {
        int sx = px + 8, sy = py + 8, sw = sbW, sh = ph - 16;
        rr(g, sx, sy, sw, sh, 8, SIDEBAR);
        ctext(g, "KWEETN", sx + sw / 2, sy + 10, YELLOW);
        ctext(g, "SKY", sx + sw / 2, sy + 21, ACCENT_SOFT);
        g.fill(sx + 14, sy + 34, sx + sw - 14, sy + 35, 0xFF1D3569);

        int ty = sy + 44;
        for (Tab t : Tab.values()) {
            boolean sel = t == tab;
            boolean hov = mx >= sx + 6 && mx < sx + sw - 6 && my >= ty && my < ty + 24;
            if (sel) rr(g, sx + 6, ty, sw - 12, 24, 7, ACCENT);
            else if (hov) rr(g, sx + 6, ty, sw - 12, 24, 7, 0xFF1A3266);
            g.drawString(this.font, t.label, sx + 16, ty + 8, sel ? 0xFFFFFFFF : TEXT, false);
            final Tab target = t;
            hits.add(new Hit(sx + 6, ty, sw - 12, 24, () -> {
                tab = target;
                scroll = 0;
            }));
            ty += 28;
        }
        ctext(g, "Press K to open", sx + sw / 2, sy + sh - 14, MUTED);
    }

    // =========================================================== SKY tab
    private int drawSkyTab(GuiGraphics g, int mx, int my, int base) {
        int gap = 8, cols = 3;
        int cardW = (cw - gap * (cols - 1)) / cols;
        int iconH = Math.round((cardW - 10) * 0.52f);
        int cardH = iconH + 10 + 18;

        int cur = 0;
        cur += grid(g, mx, my, SkyManager.I.builtin, base + cur, cols, cardW, cardH, gap, iconH);
        cur += 10; // skip a line
        text(g, "CUSTOM", cx + 2, base + cur, ACCENT_SOFT);
        cur += 16;
        List<SkyEntry> custom = SkyManager.I.custom();
        if (custom.isEmpty()) {
            text(g, "Nothing here yet. Add your own sky on the Kweetn tab.", cx + 2, base + cur, MUTED);
            cur += 16;
        } else {
            cur += grid(g, mx, my, custom, base + cur, cols, cardW, cardH, gap, iconH);
        }
        return cur;
    }

    private int grid(GuiGraphics g, int mx, int my, List<SkyEntry> list, int top, int cols, int cardW, int cardH, int gap, int iconH) {
        int rows = (list.size() + cols - 1) / cols;
        for (int i = 0; i < list.size(); i++) {
            int x = cx + (i % cols) * (cardW + gap);
            int y = top + (i / cols) * (cardH + gap);
            if (y + cardH < cy || y > cy + ch) continue; // off screen
            card(g, mx, my, list.get(i), x, y, cardW, cardH, iconH);
        }
        return rows * (cardH + gap);
    }

    private void card(GuiGraphics g, int mx, int my, SkyEntry e, int x, int y, int w, int h, int iconH) {
        boolean sel = SkyManager.I.isSelected(e);
        boolean hov = hoverContent(mx, my, x, y, w, h);
        rr(g, x, y, w, h, 7, sel ? ACCENT : (hov ? CARD_BORDER_HOVER : CARD_BORDER));
        rr(g, x + 2, y + 2, w - 4, h - 4, 6, hov ? CARD_HOVER : CARD);
        drawIcon(g, e, x + 5, y + 5, w - 10, iconH);
        ctext(g, trim(e.name(), w - 10), x + w / 2, y + iconH + 11, sel ? 0xFFFFFFFF : TEXT);
        if (sel) {
            rr(g, x + w - 27, y + 8, 20, 11, 4, ACCENT);
            ctext(g, "ON", x + w - 17, y + 10, 0xFFFFFFFF);
        }
        addContentHit(x, y, w, h, () -> SkyManager.I.select(e));
    }

    // =========================================================== CUSTOM ("Kweetn") tab
    private int drawCustomTab(GuiGraphics g, int mx, int my, int base) {
        int cur = 0;
        int dzH = 72;
        rr(g, cx, base + cur, cw, dzH, 9, PANEL_BORDER);
        rr(g, cx + 1, base + cur + 1, cw - 2, dzH - 2, 8, 0xFF0E1E44);
        ctext(g, "Drag a sky pack into this window", cx + cw / 2, base + cur + 17, TEXT);
        ctext(g, "(a .zip file or a folder)", cx + cw / 2, base + cur + 31, MUTED);
        ctext(g, "It will show up on the Sky tab under CUSTOM.", cx + cw / 2, base + cur + 49, ACCENT_SOFT);
        cur += dzH + 10;

        button(g, mx, my, cx, base + cur, 140, 18, "Open custom folder", ACCENT, () -> {
            try {
                Path dir = SkyManager.I.customDir();
                Files.createDirectories(dir);
                Util.getPlatform().openPath(dir);
            } catch (Exception ex) {
                SkyManager.I.status = "Could not open the folder.";
            }
        });
        cur += 30;

        text(g, "Your custom skies", cx, base + cur, ACCENT_SOFT);
        cur += 14;
        List<SkyEntry> custom = SkyManager.I.custom();
        if (custom.isEmpty()) {
            text(g, "None yet.", cx, base + cur, MUTED);
            cur += 14;
        }
        for (SkyEntry e : custom) {
            int y = base + cur;
            rr(g, cx, y, cw, 22, 6, CARD);
            text(g, trim(e.name(), cw - 110), cx + 8, y + 7, TEXT);
            button(g, mx, my, cx + cw - 62, y + 3, 56, 16, "Remove", DANGER, () -> SkyManager.I.removeCustom(e));
            cur += 26;
        }
        return cur;
    }

    // =========================================================== QUALITY tab
    private int drawQualityTab(GuiGraphics g, int mx, int my, int base) {
        int gap = 8;
        int w = (cw - gap) / 2;
        int h = 96;
        boolean best = SkyManager.I.config.best();
        qualityCard(g, mx, my, cx, base, w, h, "Default", !best,
                "The skies exactly as they were made. Lowest memory use.", () -> SkyManager.I.setQuality(false));
        qualityCard(g, mx, my, cx + w + gap, base, w, h, "Best", best,
                "Smaller skies are scaled up to 2048 px per side and smoothed. Takes a moment to apply.",
                () -> SkyManager.I.setQuality(true));

        int cur = h + 12;
        List<String> lines = wrap("Warning: Best uses much more video memory (up to about 135 MB per sky) and can lower your FPS on weaker graphics cards. Default is how the skies are made.", cw - 20);
        int boxH = lines.size() * 11 + 14;
        rr(g, cx, base + cur, cw, boxH, 7, 0xFFC99A1F);
        rr(g, cx + 1, base + cur + 1, cw - 2, boxH - 2, 6, 0xFF2B2110);
        int ly = base + cur + 7;
        for (String line : lines) {
            text(g, line, cx + 10, ly, WARN);
            ly += 11;
        }
        cur += boxH + 6;
        return cur;
    }

    private void qualityCard(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String title, boolean on, String desc, Runnable r) {
        boolean hov = hoverContent(mx, my, x, y, w, h);
        rr(g, x, y, w, h, 8, on ? ACCENT : (hov ? CARD_BORDER_HOVER : CARD_BORDER));
        rr(g, x + 2, y + 2, w - 4, h - 4, 7, hov ? CARD_HOVER : CARD);
        // radio dot
        rr(g, x + 10, y + 11, 11, 11, 5, on ? ACCENT : 0xFF2A417A);
        if (on) rr(g, x + 13, y + 14, 5, 5, 2, 0xFFFFFFFF);
        g.drawString(this.font, title, x + 28, y + 12, 0xFFFFFFFF, true);
        int ly = y + 30;
        for (String line : wrap(desc, w - 20)) {
            text(g, line, x + 10, ly, MUTED);
            ly += 11;
        }
        addContentHit(x, y, w, h, r);
    }

    // =========================================================== input
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.contains(mx, my)) {
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 22;
        return true;
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        SkyManager.I.addCustom(paths);
        SkyManager.I.status = "Reading dropped files...";
    }
}
