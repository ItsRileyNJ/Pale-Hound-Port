package de.cadentem.pale_hound.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StoryDifficultyState(boolean enabled) implements CustomPacketPayload {
    public static final Type<StoryDifficultyState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pale_hound", "story_difficulty"));
    public static final StreamCodec<FriendlyByteBuf, StoryDifficultyState> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeBoolean(packet.enabled()), buffer -> new StoryDifficultyState(buffer.readBoolean()));
    @Override public Type<StoryDifficultyState> type() { return TYPE; }
}
