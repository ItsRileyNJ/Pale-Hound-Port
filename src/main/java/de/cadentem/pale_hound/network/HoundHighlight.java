package de.cadentem.pale_hound.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Private outlines plus live positions when the model is outside client tracking. */
public record HoundHighlight(List<UUID> entities, int durationTicks, List<Position> positions) implements CustomPacketPayload {
    public record Position(UUID id, Vec3 position) {
        public Position {
            if (id == null || position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))
                throw new IllegalArgumentException("Invalid highlight position");
        }
    }
    public static final int MAX_ENTITIES = 1024;
    public static final Type<HoundHighlight> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("pale_hound", "highlight"));
    public static final StreamCodec<FriendlyByteBuf, HoundHighlight> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeVarInt(packet.durationTicks());
            buffer.writeVarInt(packet.entities().size());
            packet.entities().forEach(buffer::writeUUID);
            buffer.writeVarInt(packet.positions().size());
            for (var entry : packet.positions()) {
                buffer.writeUUID(entry.id());
                buffer.writeDouble(entry.position().x); buffer.writeDouble(entry.position().y); buffer.writeDouble(entry.position().z);
            }
        }, buffer -> {
            int ticks = buffer.readVarInt();
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_ENTITIES) throw new IllegalArgumentException("Invalid highlight entity count");
            var ids = new ArrayList<UUID>(count);
            for (int i = 0; i < count; ++i) ids.add(buffer.readUUID());
            count = buffer.readVarInt();
            if (count < 0 || count > MAX_ENTITIES) throw new IllegalArgumentException("Invalid highlight position count");
            var positions = new ArrayList<Position>(count);
            for (int i = 0; i < count; ++i) positions.add(new Position(buffer.readUUID(), new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())));
            return new HoundHighlight(ids, ticks, positions);
        });
    public HoundHighlight(List<UUID> entities, int durationTicks) { this(entities, durationTicks, List.of()); }
    public HoundHighlight {
        entities = List.copyOf(entities); positions = List.copyOf(positions);
        if (entities.size() > MAX_ENTITIES || positions.size() > MAX_ENTITIES || durationTicks < 0 || durationTicks > 6000) throw new IllegalArgumentException("Invalid highlight bounds");
        for (var entry : positions) if (!entities.contains(entry.id())) throw new IllegalArgumentException("Unknown highlight position");
    }
    @Override public Type<HoundHighlight> type() { return TYPE; }
}
