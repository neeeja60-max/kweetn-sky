package dev.kweetn.sky;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class KweetnSkyClient implements ClientModInitializer {
    public static KeyMapping OPEN_MENU;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("kweetnsky", "main"));
        OPEN_MENU = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.kweetnsky.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.consumeClick()) {
                if (client.screen == null) client.setScreen(new KweetnScreen(null));
            }
        });

        // "KWEETN" in yellow under the Minecraft logo on the title screen
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> {
                    Font f = client.font;
                    String t = "KWEETN";
                    graphics.drawString(f, t, s.width / 2 - f.width(t) / 2, 78, 0xFFFFD43B, true);
                });
            }
        });

        SkyManager.I.refreshCustom();
    }
}
