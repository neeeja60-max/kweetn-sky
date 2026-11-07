package dev.kweetn.sky;

import java.nio.file.Path;

/** One selectable sky. Built-in skies live inside the mod jar, custom ones in .minecraft/kweetn_sky/custom. */
public record SkyEntry(String key, String name, boolean builtin, Path path, String resource) {
}
