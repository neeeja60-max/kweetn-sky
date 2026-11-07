package dev.kweetn.sky;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Handles everything about skies: the built-in list, the custom list, icons and switching.
 * A sky is switched by putting its resource pack into .minecraft/resourcepacks (as kweetn_sky_*.zip),
 * selecting it on top of the other packs and reloading the resources.
 * The sky itself is shown by whatever mod already reads OptiFine-style sky packs (e.g. Nuit + Nuit Interop).
 */
public final class SkyManager {
    public static final SkyManager I = new SkyManager();

    public record Icon(Identifier id, int w, int h) {
    }

    public final SkyConfig config = SkyConfig.load();
    public final List<SkyEntry> builtin = List.of(
            b("white", "White"), b("purple", "Purple"), b("black", "Black"), b("blue", "Blue"),
            b("orange", "Orange"), b("sunset", "Sunset"), b("eclipse", "Eclipse"), b("aurora", "Aurora"));

    private final List<SkyEntry> custom = new ArrayList<>();
    private final Map<String, Icon> icons = new HashMap<>();
    private final Set<String> iconFailed = new HashSet<>();

    public volatile String status = "";
    public volatile boolean busy = false;

    private SkyManager() {
    }

    private static SkyEntry b(String id, String name) {
        return new SkyEntry("b:" + id, name, true, null, id);
    }

    // ------------------------------------------------------------------ paths
    private Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public Path customDir() {
        return gameDir().resolve("kweetn_sky").resolve("custom");
    }

    private Path packsDir() {
        return gameDir().resolve("resourcepacks");
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    // ------------------------------------------------------------------ custom list
    public synchronized void refreshCustom() {
        custom.clear();
        try {
            Files.createDirectories(customDir());
            try (Stream<Path> s = Files.list(customDir())) {
                s.sorted().forEach(p -> {
                    String n = p.getFileName().toString();
                    if (Files.isDirectory(p)) {
                        custom.add(new SkyEntry("c:" + n, n, false, p, null));
                    } else if (n.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        custom.add(new SkyEntry("c:" + n, n.substring(0, n.length() - 4), false, p, null));
                    }
                });
            }
        } catch (IOException ignored) {
        }
    }

    public synchronized List<SkyEntry> custom() {
        return new ArrayList<>(custom);
    }

    public synchronized SkyEntry find(String key) {
        for (SkyEntry e : builtin) if (e.key().equals(key)) return e;
        for (SkyEntry e : custom) if (e.key().equals(key)) return e;
        return null;
    }

    public boolean isSelected(SkyEntry e) {
        return e.key().equals(config.selected);
    }

    // ------------------------------------------------------------------ icons (pack.png of each sky)
    public Icon icon(SkyEntry e) {
        Icon cached = icons.get(e.key());
        if (cached != null) return cached;
        if (iconFailed.contains(e.key())) return null;
        try (InputStream in = readPackPng(e)) {
            if (in == null) {
                iconFailed.add(e.key());
                return null;
            }
            NativeImage img = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath("kweetnsky", "icon/" + sanitize(e.key()));
            String label = "kweetn_icon_" + e.key();
            DynamicTexture tex = new DynamicTexture(() -> label, img);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            Icon ic = new Icon(id, img.getWidth(), img.getHeight());
            icons.put(e.key(), ic);
            return ic;
        } catch (Throwable ex) {
            iconFailed.add(e.key());
            return null;
        }
    }

    private void releaseIcon(String key) {
        Icon ic = icons.remove(key);
        iconFailed.remove(key);
        if (ic != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(ic.id());
            } catch (Throwable ignored) {
            }
        }
    }

    private InputStream readPackPng(SkyEntry e) throws IOException {
        if (e.builtin()) {
            InputStream raw = SkyManager.class.getResourceAsStream("/assets/kweetnsky/skies/" + e.resource() + ".zip");
            if (raw == null) return null;
            ZipInputStream zin = new ZipInputStream(raw);
            ZipEntry en;
            while ((en = zin.getNextEntry()) != null) {
                if (en.getName().equals("pack.png")) {
                    byte[] data = zin.readAllBytes();
                    zin.close();
                    return new ByteArrayInputStream(data);
                }
            }
            zin.close();
            return null;
        }
        Path p = e.path();
        if (Files.isDirectory(p)) {
            Path png = p.resolve("pack.png");
            return Files.exists(png) ? Files.newInputStream(png) : null;
        }
        try (ZipFile zf = new ZipFile(p.toFile())) {
            ZipEntry en = zf.getEntry("pack.png");
            if (en == null) return null;
            return new ByteArrayInputStream(zf.getInputStream(en).readAllBytes());
        }
    }

    // ------------------------------------------------------------------ actions from the menu
    public void select(SkyEntry e) {
        if (busy) return;
        if (isSelected(e)) apply(null); else apply(e);
    }

    public void setQuality(boolean best) {
        if (busy || best == config.best()) return;
        config.quality = best ? "best" : "default";
        config.save();
        SkyEntry cur = find(config.selected);
        if (cur != null) apply(cur);
    }

    public void addCustom(List<Path> paths) {
        Thread t = new Thread(() -> {
            int added = 0;
            List<String> bad = new ArrayList<>();
            for (Path p : paths) {
                String n = p.getFileName() == null ? "?" : p.getFileName().toString();
                try {
                    if (Files.isDirectory(p)) {
                        if (!Files.exists(p.resolve("pack.mcmeta"))) {
                            bad.add(n);
                            continue;
                        }
                        copyTree(p, unique(customDir().resolve(n)));
                        added++;
                    } else if (n.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        if (!zipHasPackMcmeta(p)) {
                            bad.add(n);
                            continue;
                        }
                        Files.createDirectories(customDir());
                        Files.copy(p, unique(customDir().resolve(n)));
                        added++;
                    } else {
                        bad.add(n);
                    }
                } catch (Exception ex) {
                    bad.add(n);
                }
            }
            refreshCustom();
            StringBuilder sb = new StringBuilder();
            if (added > 0) sb.append("Added ").append(added).append(added == 1 ? " sky" : " skies").append(" to CUSTOM.");
            if (!bad.isEmpty()) sb.append(sb.length() > 0 ? " " : "").append("Not a sky pack: ").append(String.join(", ", bad));
            status = sb.toString();
        }, "kweetn-sky-add");
        t.setDaemon(true);
        t.start();
    }

    public void removeCustom(SkyEntry e) {
        if (e.builtin() || busy) return;
        if (isSelected(e)) apply(null);
        try {
            deleteTree(e.path());
            status = "Removed " + e.name() + ".";
        } catch (Exception ex) {
            status = "Could not remove " + e.name() + ".";
        }
        releaseIcon(e.key());
        refreshCustom();
    }

    // ------------------------------------------------------------------ switching
    private void apply(SkyEntry e) {
        busy = true;
        status = e == null ? "Turning the sky off..." : "Applying " + e.name() + "...";
        final boolean best = config.best();
        Thread t = new Thread(() -> {
            try {
                String fileName = null;
                if (e != null) {
                    Files.createDirectories(packsDir());
                    fileName = "kweetn_sky_" + sanitize(e.key()) + (best ? "_best" : "") + ".zip";
                    Path target = packsDir().resolve(fileName);
                    if (!Files.exists(target)) build(e, target, best);
                }
                config.selected = e == null ? "" : e.key();
                config.save();
                final String packId = fileName == null ? null : "file/" + fileName;
                final String keep = fileName;
                Minecraft.getInstance().execute(() -> {
                    try {
                        enable(packId);
                        status = e == null ? "Sky turned off." : "Sky applied: " + e.name();
                    } catch (Throwable ex) {
                        status = "Could not apply the sky: " + ex.getMessage();
                    }
                    busy = false;
                });
                scheduleCleanup(keep);
            } catch (Throwable ex) {
                status = "Failed: " + ex.getMessage();
                busy = false;
            }
        }, "kweetn-sky-apply");
        t.setDaemon(true);
        t.start();
    }

    /** Runs on the main thread: select (or deselect) our pack on top of the other packs and reload. */
    private void enable(String packId) {
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        selected.removeIf(s -> s.startsWith("file/kweetn_sky_"));
        if (packId != null && repo.getAvailableIds().contains(packId)) selected.add(packId);
        repo.setSelected(selected);
        mc.options.updateResourcePacks(repo);
    }

    private void build(SkyEntry e, Path target, boolean best) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try (InputStream in = openSource(e); OutputStream out = Files.newOutputStream(tmp)) {
            if (best) BestQuality.process(in, out); else in.transferTo(out);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private InputStream openSource(SkyEntry e) throws IOException {
        if (e.builtin()) {
            InputStream in = SkyManager.class.getResourceAsStream("/assets/kweetnsky/skies/" + e.resource() + ".zip");
            if (in == null) throw new IOException("Missing built-in sky: " + e.resource());
            return in;
        }
        if (Files.isDirectory(e.path())) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            zipDirectory(e.path(), bo);
            return new ByteArrayInputStream(bo.toByteArray());
        }
        return Files.newInputStream(e.path());
    }

    /** Old kweetn_sky_*.zip files cannot be deleted while the game still reads them, so try again a bit later. */
    private void scheduleCleanup(String keepName) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(8000);
                try (Stream<Path> s = Files.list(packsDir())) {
                    s.forEach(p -> {
                        String n = p.getFileName().toString();
                        if (n.startsWith("kweetn_sky_") && !n.equals(keepName)) {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        }
                    });
                }
            } catch (Throwable ignored) {
            }
        }, "kweetn-sky-cleanup");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ file helpers
    private static boolean zipHasPackMcmeta(Path p) {
        try (ZipFile zf = new ZipFile(p.toFile())) {
            return zf.getEntry("pack.mcmeta") != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private static Path unique(Path p) {
        if (!Files.exists(p)) return p;
        String n = p.getFileName().toString();
        int dot = n.lastIndexOf('.');
        String base = dot > 0 && !Files.isDirectory(p) ? n.substring(0, dot) : n;
        String ext = dot > 0 && !Files.isDirectory(p) ? n.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            Path c = p.resolveSibling(base + "_" + i + ext);
            if (!Files.exists(c)) return c;
        }
        return p;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path src : (Iterable<Path>) s::iterator) {
                Path dst = to.resolve(from.relativize(src).toString());
                if (Files.isDirectory(src)) Files.createDirectories(dst);
                else {
                    Files.createDirectories(dst.getParent());
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.walk(p)) {
                for (Path x : (Iterable<Path>) s.sorted(Comparator.reverseOrder())::iterator) Files.delete(x);
            }
        } else {
            Files.deleteIfExists(p);
        }
    }

    private static void zipDirectory(Path dir, OutputStream out) throws IOException {
        try (ZipOutputStream zout = new ZipOutputStream(out); Stream<Path> s = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                String name = dir.relativize(f).toString().replace('\\', '/');
                zout.putNextEntry(new ZipEntry(name));
                Files.copy(f, zout);
                zout.closeEntry();
            }
        }
    }
}
