package dev.iosmusic;

import com.mojang.blaze3d.platform.InputConstants;
import dev.iosmusic.api.ApiClient;
import dev.iosmusic.together.TogetherClient;
import dev.iosmusic.ui.HudLayer;
import dev.iosmusic.ui.MusicScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class IosMusicFabric implements ClientModInitializer {
    public static final String MODID = "iosmusic";

    public static final KeyMapping OPEN = new KeyMapping("key.iosmusic.open",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.iosmusic");
    public static final KeyMapping PLAY_PAUSE = new KeyMapping("key.iosmusic.play_pause",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.iosmusic");
    public static final KeyMapping NEXT = new KeyMapping("key.iosmusic.next",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.iosmusic");
    public static final KeyMapping PREVIOUS = new KeyMapping("key.iosmusic.previous",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, "key.categories.iosmusic");
    public static final KeyMapping TOGETHER = new KeyMapping("key.iosmusic.together",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.iosmusic");

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(OPEN);
        KeyBindingHelper.registerKeyBinding(PLAY_PAUSE);
        KeyBindingHelper.registerKeyBinding(NEXT);
        KeyBindingHelper.registerKeyBinding(PREVIOUS);
        KeyBindingHelper.registerKeyBinding(TOGETHER);

        ModConfig.get();
        ApiClient.get().init();

        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> HudLayer.render(graphics, deltaTracker));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TogetherClient.reset());
    }

    private void tick(Minecraft minecraft) {
        TogetherClient.tick(minecraft);
        if (TogetherClient.consumeOpenRequest()) {
            if (minecraft.screen == null || minecraft.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                MusicScreen screen = new MusicScreen();
                minecraft.setScreen(screen);
                screen.openTogether();
            } else if (minecraft.screen instanceof MusicScreen screen) {
                screen.openTogether();
            }
        }
        while (OPEN.consumeClick()) {
            togglePlayer(minecraft);
        }
        while (PLAY_PAUSE.consumeClick()) {
            MusicPlayer.get().toggle();
        }
        while (NEXT.consumeClick()) {
            MusicPlayer.get().next();
        }
        while (PREVIOUS.consumeClick()) {
            MusicPlayer.get().previous();
        }
        while (TOGETHER.consumeClick()) {
            if (minecraft.screen == null
                    || minecraft.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                MusicScreen screen = new MusicScreen();
                minecraft.setScreen(screen);
                screen.openTogether();
            } else if (minecraft.screen instanceof MusicScreen screen) {
                screen.toggleTogether();
            }
        }
    }

    private void togglePlayer(Minecraft minecraft) {
        if (minecraft.screen == null || minecraft.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            minecraft.setScreen(new MusicScreen());
        } else if (minecraft.screen instanceof MusicScreen screen) {
            screen.onClose();
        }
    }
}
