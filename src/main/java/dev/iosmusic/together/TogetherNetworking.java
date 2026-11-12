package dev.iosmusic.together;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Registers the optional together-listening channel and provides the send helpers. */
public final class TogetherNetworking {
    private TogetherNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional()
                .playBidirectional(TogetherPayload.TYPE, TogetherPayload.CODEC, TogetherNetworking::handle);
    }

    private static void handle(TogetherPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            TogetherMessage message = TogetherMessage.parse(payload.json());
            if (context.flow() == PacketFlow.SERVERBOUND) {
                if (context.player() instanceof ServerPlayer player) {
                    TogetherServer.handle(player, message);
                }
            } else {
                TogetherClientBridge.handle(message);
            }
        });
    }

    public static void toServer(TogetherMessage message) {
        PacketDistributor.sendToServer(new TogetherPayload(message.json()));
    }

    public static void toPlayer(ServerPlayer player, TogetherMessage message) {
        PacketDistributor.sendToPlayer(player, new TogetherPayload(message.json()));
    }
}
