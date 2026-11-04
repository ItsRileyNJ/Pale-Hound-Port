package de.cadentem.pale_hound.commands;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.network.HoundHighlight;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Refresh existing selections without forcing chunks or extending the requested duration. */
@EventBusSubscriber(modid = "pale_hound")
public final class HoundHighlightTracker {
    private record Session(ResourceKey<Level> dimension, List<UUID> ids, int expires) {}
    private static final HashMap<UUID, Session> sessions = new HashMap<>();
    private HoundHighlightTracker() {}
    public static void start(ServerPlayer player, List<PaleHoundEntity> hounds, int ticks) {
        if (hounds.isEmpty()) { stop(player); return; }
        var session = new Session(player.level().dimension(), hounds.stream().map(PaleHoundEntity::getUUID).toList(), player.getServer().getTickCount() + ticks);
        sessions.put(player.getUUID(), session);
        send(player, session, ticks);
    }
    public static void stop(ServerPlayer player) {
        sessions.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, new HoundHighlight(List.of(), 0));
    }
    private static boolean send(ServerPlayer player, Session session, int remaining) {
        var positions = session.ids.stream().map(player.serverLevel()::getEntity)
            .filter(entity -> entity instanceof PaleHoundEntity && !entity.isRemoved())
            .map(entity -> new HoundHighlight.Position(entity.getUUID(), entity.position())).toList();
        if (positions.isEmpty()) return false;
        PacketDistributor.sendToPlayer(player, new HoundHighlight(positions.stream().map(HoundHighlight.Position::id).toList(), remaining, positions));
        return true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 0) return;
        var iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); var session = entry.getValue();
            var player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) { iterator.remove(); continue; }
            int remaining = session.expires - event.getServer().getTickCount();
            boolean permitted = HoundHighlightCommand.canUse(player.createCommandSourceStack());
            boolean valid = remaining > 0 && player.level().dimension().equals(session.dimension) && permitted;
            if (valid && send(player, session, remaining)) continue;
            iterator.remove();
            PacketDistributor.sendToPlayer(player, new HoundHighlight(List.of(), 0));
            if (valid) player.displayClientMessage(Component.literal("Highlighted Pale Hound is no longer loaded in this dimension."), true);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { sessions.clear(); }
}
