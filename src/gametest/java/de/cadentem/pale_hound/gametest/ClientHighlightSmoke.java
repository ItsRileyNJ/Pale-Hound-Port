package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.client.HoundHighlights;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Private outline and live marker behavior across tracking loss, return, expiry and actual removal. */
@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class ClientHighlightSmoke {
    private static volatile int entityId = -1;
    private static volatile java.util.UUID entityUuid;
    private static boolean requested;
    private static int phase, ticks, marked;
    private static void move(Minecraft client, double offset) {
        var id = client.player.getUUID(); var server = client.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            var hound = player.serverLevel().getEntity(entityId);
            if (hound == null) throw new AssertionError("Expected Hound to remain server loaded");
            player.serverLevel().getChunk(net.minecraft.core.BlockPos.containing(player.position().add(offset, 0, 0)));
            hound.setPos(player.position().add(offset, 0, 0));
        });
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("pale_hound.clientHighlightSmoke")) return;
        var client = Minecraft.getInstance();
        if (++ticks > 2400) throw new AssertionError("Highlight smoke timed out at " + phase);
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        client.options.pauseOnLostFocus = false;
        if (!requested) {
            requested = true; client.options.renderDistance().set(8); client.options.simulationDistance().set(10); client.options.broadcastOptions();
            var id = client.player.getUUID(); var server = client.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id); var level = player.serverLevel();
                player.setGameMode(GameType.CREATIVE);
                for (int x = -2; x <= 7; ++x) for (int z = -2; z <= 2; ++z) level.getChunk(512 + x, 512 + z);
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 8192, 8192);
                player.teleportTo(8192.5, y, 8192.5);
                var old = new java.util.ArrayList<PaleHoundEntity>();
                for (var entity : level.getAllEntities()) if (entity instanceof PaleHoundEntity hound) old.add(hound);
                old.forEach(PaleHoundEntity::discard);
                level.setDayTime(18000); level.updateSkyBrightness();
                var hound = de.cadentem.pale_hound.util.Utils.trySpawnMob(player, ModEntityTypes.PALE_HOUND.get(),
                    net.minecraft.world.entity.MobSpawnType.TRIGGERED, level, player.blockPosition().offset(35, 0, 0), 1, 0, 3,
                    net.minecraft.util.SpawnUtil.Strategy.ON_TOP_OF_COLLIDER).orElseThrow(() -> new AssertionError("Native spawn failed"));
                hound.hasSpawned = true; hound.setInvisible(true); hound.setNoAi(true); hound.setNoGravity(true); hound.setSilent(true);
                entityId = hound.getId(); entityUuid = hound.getUUID();
            });
        }
        var hound = client.level.getEntity(entityId) instanceof PaleHoundEntity h ? h : null;
        if (phase == 0 && hound != null) {
            client.player.connection.sendCommand("pale_hound highlight 60"); phase = 1;
        } else if (phase == 1 && hound != null && HoundHighlights.isHighlighted(hound) && !HoundHighlights.positions().isEmpty()) {
            if (!client.shouldEntityAppearGlowing(hound) || hound.isCurrentlyGlowing()) throw new AssertionError("Outline was not private");
            move(client, 112); phase = 2;
        } else if (phase == 2 && hound == null && HoundHighlights.positions().stream().anyMatch(p -> p.id().equals(entityUuid)
                    && p.position().distanceTo(client.player.position()) > 100)) {
            if (HoundHighlights.hasTrackedModel(entityUuid)) throw new AssertionError("Out-of-range model is still tracked");
            marked = ticks; phase = 3;
        } else if (phase == 3 && ticks > marked + 20) {
            Screenshot.grab(client.gameDirectory, "highlight-outside-view.png", client.getMainRenderTarget(), message -> {});
            move(client, 24); phase = 4;
        } else if (phase == 4 && hound != null && HoundHighlights.isHighlighted(hound)) {
            if (!client.shouldEntityAppearGlowing(hound)) throw new AssertionError("Outline did not resume on tracking return");
            client.player.connection.sendCommand("pale_hound highlight off"); phase = 5;
        } else if (phase == 5 && hound != null && HoundHighlights.positions().isEmpty() && !HoundHighlights.isHighlighted(hound)) {
            client.player.connection.sendCommand("pale_hound highlight 2"); phase = 6;
        } else if (phase == 6 && hound != null && HoundHighlights.isHighlighted(hound)) {
            marked = ticks; phase = 7;
        } else if (phase == 7 && ticks > marked + 45 && hound != null && !HoundHighlights.isHighlighted(hound)) {
            if (!HoundHighlights.positions().isEmpty()) throw new AssertionError("Live updates extended expiry");
            client.player.connection.sendCommand("pale_hound highlight 30"); phase = 8;
        } else if (phase == 8 && hound != null && HoundHighlights.isHighlighted(hound)) {
            var server = client.getSingleplayerServer(); server.execute(() -> server.overworld().getEntity(entityId).discard()); phase = 9;
        } else if (phase == 9 && hound == null && HoundHighlights.positions().isEmpty()) {
            PaleHound.LOG.info("CLIENT HIGHLIGHT SMOKE PASSED: private native outline, live marker outside range, tracking return, off, fixed expiry and actual removal");
            phase = 10; client.stop();
        }
    }
}
