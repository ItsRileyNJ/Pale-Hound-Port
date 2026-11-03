package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.client.HoundHighlights;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Integrated-client smoke test, excluded from the published mod JAR. */
@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class ClientHighlightSmoke {
    private static volatile int entityId = -1;
    private static boolean requestedSpawn;
    private static int phase;
    private static int ticks;
    private static int phaseStarted;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("pale_hound.clientHighlightSmoke")) return;
        var client = Minecraft.getInstance();
        if (++ticks > 2400) throw new AssertionError("Client highlight smoke test timed out in phase " + phase);
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        client.options.pauseOnLostFocus = false;
        if (!requestedSpawn) {
            requestedSpawn = true;
            var uuid = client.player.getUUID();
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid);
                player.setGameMode(GameType.CREATIVE);
                var level = player.serverLevel();
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    player.blockPosition().getX(), player.blockPosition().getZ());
                player.teleportTo(player.getX(), y + 1, player.getZ());
                var hound = ModEntityTypes.PALE_HOUND.get().create(level);
                hound.setNoAi(true);
                hound.setNoGravity(true);
                hound.setPos(player.position().add(3, 0, 0));
                level.addFreshEntity(hound);
                entityId = hound.getId();
            });
        }
        if (!(client.level.getEntity(entityId) instanceof PaleHoundEntity hound)) return;
        if (phase == 0) {
            client.player.connection.sendCommand("pale_hound highlight");
            phase = 1;
        } else if (phase == 1 && HoundHighlights.isHighlighted(hound)) {
            if (!client.shouldEntityAppearGlowing(hound) || hound.isCurrentlyGlowing())
                throw new AssertionError("Viewer outline did not stay separate from global glowing state");
            client.player.connection.sendCommand("pale_hound highlight off");
            phase = 2;
        } else if (phase == 2 && !HoundHighlights.isHighlighted(hound)) {
            if (client.shouldEntityAppearGlowing(hound)) throw new AssertionError("Outline remained after off");
            client.player.connection.sendCommand("pale_hound highlight 2");
            phase = 3;
        } else if (phase == 3 && HoundHighlights.isHighlighted(hound)) {
            phaseStarted = ticks;
            phase = 4;
        } else if (phase == 4 && ticks > phaseStarted + 40 && !HoundHighlights.isHighlighted(hound)) {
            if (client.shouldEntityAppearGlowing(hound)) throw new AssertionError("Outline did not expire");
            PaleHound.LOG.info("CLIENT HIGHLIGHT SMOKE PASSED: private outline, off, and expiration verified");
            phase = 5;
            client.stop();
        }
    }
}
