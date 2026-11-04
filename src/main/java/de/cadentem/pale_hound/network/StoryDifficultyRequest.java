package de.cadentem.pale_hound.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** GUI activation; ordinary difficulties continue using vanilla's packet. */
public record StoryDifficultyRequest() implements CustomPacketPayload {
    public static final Type<StoryDifficultyRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pale_hound", "select_story"));
    public static final StreamCodec<FriendlyByteBuf, StoryDifficultyRequest> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {}, buffer -> new StoryDifficultyRequest());
    @Override public Type<StoryDifficultyRequest> type() { return TYPE; }
}
