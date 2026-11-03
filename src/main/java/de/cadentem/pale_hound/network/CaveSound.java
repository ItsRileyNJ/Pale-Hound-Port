package de.cadentem.pale_hound.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-bound positional sound, retaining the original packet's field order. */
public final class CaveSound implements CustomPacketPayload {
    public static final Type<CaveSound> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pale_hound", "cave_sound"));
    public static final StreamCodec<FriendlyByteBuf, CaveSound> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> packet.encode(buffer), CaveSound::decode);

    public final ResourceLocation soundResource;
    public final BlockPos playerPosition;
    public final float volume;
    public final float pitch;

    public CaveSound(ResourceLocation soundResource, BlockPos playerPosition, float volume, float pitch) {
        this.soundResource = soundResource;
        this.playerPosition = playerPosition.immutable();
        this.volume = volume;
        this.pitch = pitch;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(soundResource);
        buffer.writeBlockPos(playerPosition);
        buffer.writeFloat(volume);
        buffer.writeFloat(pitch);
    }

    public static CaveSound decode(FriendlyByteBuf buffer) {
        return new CaveSound(buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readFloat(), buffer.readFloat());
    }

    @Override
    public Type<CaveSound> type() {
        return TYPE;
    }
}
