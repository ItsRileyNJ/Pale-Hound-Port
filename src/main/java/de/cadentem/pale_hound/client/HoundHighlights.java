package de.cadentem.pale_hound.client;

import de.cadentem.pale_hound.network.HoundHighlight;
import java.util.Set;
import java.util.List;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
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
    private static List<HoundHighlight.Position> positions = List.of();

    private HoundHighlights() {}

    public static void handle(HoundHighlight packet) {
        clear();
        if (packet.durationTicks() == 0 || packet.entities().isEmpty()) return;
        level = Minecraft.getInstance().level;
        if (level == null) return;
        entities = Set.copyOf(packet.entities());
        positions = packet.positions();
        expiresAt = level.getGameTime() + packet.durationTicks();
    }

    public static boolean isHighlighted(Entity entity) {
        return level != null && entity.level() == level && level.getGameTime() < expiresAt
            && entities.contains(entity.getUUID());
    }

    public static List<HoundHighlight.Position> positions() { return positions; }
    public static boolean hasTrackedModel(UUID id) {
        if (level == null) return false;
        for (var entity : level.entitiesForRendering()) if (entity instanceof de.cadentem.pale_hound.entities.PaleHoundEntity && entity.getUUID().equals(id)) return true;
        return false;
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var client = Minecraft.getInstance();
        if (level == null || client.level != level || client.player == null || client.options.hideGui || positions.isEmpty()) return;
        var nearest = positions.stream().sorted(java.util.Comparator.comparingDouble(entry -> entry.position().distanceToSqr(client.player.position()))).limit(3).toList();
        var lines = new java.util.ArrayList<String>();
        lines.add("Pale Hound highlight: " + entities.size() + " (" + Math.max(0, (expiresAt - level.getGameTime() + 19) / 20) + "s)");
        for (var entry : nearest) {
            var delta = entry.position().subtract(client.player.position());
            int direction = Math.floorMod((int)Math.round(Math.atan2(delta.x, -delta.z) * 4 / Math.PI), 8);
            String bearing = new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"}[direction];
            lines.add(bearing + " " + Math.round(delta.length()) + " blocks (" + (hasTrackedModel(entry.id()) ? "outline" : "outside view") + ")");
            var pos = net.minecraft.core.BlockPos.containing(entry.position());
            lines.add("XYZ " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
        int width = lines.stream().mapToInt(client.font::width).max().orElse(0);
        int x = Math.max(6, event.getGuiGraphics().guiWidth() - width - 10), y = 6;
        event.getGuiGraphics().fill(x - 4, y - 3, x + width + 4, y + lines.size() * 10 + 2, 0xA0000000);
        for (String line : lines) { event.getGuiGraphics().drawString(client.font, line, x, y, 0xFFDDDDFF, true); y += 10; }
    }

    private static void clear() {
        level = null;
        entities = Set.of();
        positions = List.of();
        expiresAt = 0;
    }

    @SubscribeEvent public static void onTick(ClientTickEvent.Post event) {
        if (level != null && (Minecraft.getInstance().level != level || level.getGameTime() >= expiresAt)) clear();
    }

    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
