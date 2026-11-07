package dev.kweetn.sky;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SkyConfig {
    public String selected = "";
    public String quality = "default";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("kweetn_sky.json");
    }

    public static SkyConfig load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                SkyConfig c = GSON.fromJson(Files.readString(f), SkyConfig.class);
                if (c != null) {
                    if (c.selected == null) c.selected = "";
                    if (c.quality == null) c.quality = "default";
                    return c;
                }
            }
        } catch (Exception ignored) {
        }
        return new SkyConfig();
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(this));
        } catch (Exception ignored) {
        }
    }

    public boolean best() {
        return "best".equals(quality);
    }
}
