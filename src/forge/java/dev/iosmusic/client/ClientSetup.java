package dev.iosmusic.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.iosmusic.IosMusic;
import dev.iosmusic.ModConfig;
import dev.iosmusic.MusicPlayer;
import dev.iosmusic.api.ApiClient;
import dev.iosmusic.together.TogetherClient;
import dev.iosmusic.ui.GlassBlur;
import dev.iosmusic.ui.GlassRefraction;
import dev.iosmusic.ui.GlassShader;
import dev.iosmusic.ui.HudLayer;
import dev.iosmusic.ui.MusicScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;

import java.util.logging.Logger;

public final class ClientSetup {
    private static final Logger LOG = Logger.getLogger("iOSMusic");

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

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::clientSetup);
        modBus.addListener(ClientSetup::registerKeys);
        modBus.addListener(ClientSetup::registerLayers);
        modBus.addListener(GlassShader::register);
        modBus.addListener(GlassRefraction::register);
        modBus.addListener(GlassBlur::register);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::clientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onLoggingOut);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModConfig.get();
            ApiClient.get().init();
            LOG.info("iOS Music Forge client setup complete");
        });
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
        event.register(PLAY_PAUSE);
        event.register(NEXT);
        event.register(PREVIOUS);
        event.register(TOGETHER);
        LOG.info("Registered key mapping " + OPEN.getName());
    }

    private static void registerLayers(AddGuiOverlayLayersEvent event) {
        // Append instead of addAbove(target): the 3-arg addAbove overload targets a layer by our
        // own name on Forge 52.1.19, which logs "layer iosmusic:now_playing was not found".
        event.getLayeredDraw().add(ResourceLocation.fromNamespaceAndPath(IosMusic.MODID, "now_playing"),
                (graphics, deltaTracker) -> HudLayer.render(graphics, deltaTracker));
    }

    private static void clientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
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

    private static void togglePlayer(Minecraft minecraft) {
        if (minecraft.screen == null || minecraft.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
            minecraft.setScreen(new MusicScreen());
        } else if (minecraft.screen instanceof MusicScreen screen) {
            screen.onClose();
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TogetherClient.reset();
    }
}
