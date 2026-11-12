package dev.iosmusic.together;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Single channel carrying JSON; the optional registrar keeps vanilla/NeoForge servers compatible. */
public record TogetherPayload(String json) implements CustomPacketPayload {
    public static final Type<TogetherPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("iosmusic", "together"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TogetherPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.json, 32767),
            buffer -> new TogetherPayload(buffer.readUtf(32767)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
