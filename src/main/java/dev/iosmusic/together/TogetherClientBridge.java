package dev.iosmusic.together;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Keeps the client handler out of the dedicated-server class-loading path. */
public final class TogetherClientBridge {
    private TogetherClientBridge() {
    }

    public static void handle(TogetherMessage message) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TogetherClient.handle(message);
        }
    }
}
