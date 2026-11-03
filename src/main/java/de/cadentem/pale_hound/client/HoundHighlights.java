package de.cadentem.pale_hound.client;

import de.cadentem.pale_hound.network.HoundHighlight;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class HoundHighlights {
    private static Set<UUID> entities = Set.of();
    private static ClientLevel level;
    private static long expiresAt;

    private HoundHighlights() {}

    public static void handle(HoundHighlight packet) {
        clear();
        if (packet.durationTicks() == 0 || packet.entities().isEmpty()) return;
        level = Minecraft.getInstance().level;
        if (level == null) return;
        entities = Set.copyOf(packet.entities());
        expiresAt = level.getGameTime() + packet.durationTicks();
    }

    public static boolean isHighlighted(Entity entity) {
        return level != null && entity.level() == level && level.getGameTime() < expiresAt
            && entities.contains(entity.getUUID());
    }

    private static void clear() {
        level = null;
        entities = Set.of();
        expiresAt = 0;
    }

    @SubscribeEvent public static void onTick(ClientTickEvent.Post event) {
        if (level != null && (Minecraft.getInstance().level != level || level.getGameTime() >= expiresAt)) clear();
    }

    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
