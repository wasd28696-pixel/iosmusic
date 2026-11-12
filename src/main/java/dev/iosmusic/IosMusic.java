package dev.iosmusic;

import dev.iosmusic.client.ClientSetup;
import dev.iosmusic.together.TogetherNetworking;
import dev.iosmusic.together.TogetherServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

import java.util.logging.Logger;

@Mod(value = IosMusic.MODID)
public final class IosMusic {
    public static final String MODID = "iosmusic";
    private static final Logger LOG = Logger.getLogger("iOSMusic");

    public IosMusic(IEventBus modBus, ModContainer container) {
        LOG.info("iOS Music initializing");
        modBus.addListener(TogetherNetworking::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modBus);
        }
        NeoForge.EVENT_BUS.addListener(TogetherServer::onLogout);
    }
}
