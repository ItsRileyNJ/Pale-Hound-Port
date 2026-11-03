package de.cadentem.pale_hound.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Personal playback cues; a source UUID lets stalking playback stop with its encounter. */
public record HoundSoundCue(Kind kind, UUID source) implements CustomPacketPayload {
    public static final Type<HoundSoundCue> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pale_hound", "sound_cue"));
    public static final StreamCodec<FriendlyByteBuf, HoundSoundCue> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> { buffer.writeEnum(packet.kind()); buffer.writeUUID(packet.source()); },
        buffer -> new HoundSoundCue(buffer.readEnum(Kind.class), buffer.readUUID()));

    public enum Kind {
        AMBIENT_CRYING("ambient_crying", 1, 0.8F),
        STALKING_UNDERGROUND("stalking_underground", 2, 0.9F),
        SEES_PLAYER("sees_player", 3, 1.0F),
        SEES_PLAYER_SURFACE("sees_player_surface", 3, 1.0F),
        STOP_STALKING(null, 0, 0);

        public final String sound;
        public final int priority;
        public final float volume;
        Kind(String sound, int priority, float volume) { this.sound = sound; this.priority = priority; this.volume = volume; }
        public ResourceLocation soundId() { return ResourceLocation.fromNamespaceAndPath("pale_hound", sound); }
    }

    @Override public Type<HoundSoundCue> type() { return TYPE; }
}
