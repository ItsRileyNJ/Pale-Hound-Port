package de.cadentem.pale_hound.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent only to the player who requested the outline; never changes server entity flags. */
public record HoundHighlight(List<UUID> entities, int durationTicks) implements CustomPacketPayload {
    public static final int MAX_ENTITIES = 1024;
    public static final Type<HoundHighlight> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pale_hound", "highlight"));
    public static final StreamCodec<FriendlyByteBuf, HoundHighlight> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeVarInt(packet.durationTicks());
            buffer.writeVarInt(packet.entities().size());
            packet.entities().forEach(buffer::writeUUID);
        }, buffer -> {
            int ticks = buffer.readVarInt();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_ENTITIES) throw new IllegalArgumentException("Invalid highlight entity count");
            var ids = new ArrayList<UUID>(count);
            for (int i = 0; i < count; ++i) ids.add(buffer.readUUID());
            return new HoundHighlight(ids, ticks);
        });

    public HoundHighlight {
        entities = List.copyOf(entities);
        if (entities.size() > MAX_ENTITIES || durationTicks < 0 || durationTicks > 6000)
            throw new IllegalArgumentException("Invalid highlight bounds");
    }

    @Override public Type<HoundHighlight> type() { return TYPE; }
}
