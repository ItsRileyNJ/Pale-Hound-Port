package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.entities.goals.Roll;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import de.cadentem.pale_hound.util.Utils;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Disposable-world test of pristine negative-Y flat ground and distant crouched rendering. */
@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class ClientSuperflatSmoke {
    private static int phase, ticks, marked;
    private static volatile int entityId = -1;
    private static volatile boolean prepared, captured;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("pale_hound.clientSuperflatSmoke")) return;
        var client = Minecraft.getInstance();
        if (++ticks > 1800) throw new AssertionError("Superflat smoke timed out in phase " + phase);
        if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
        client.options.pauseOnLostFocus = false;
        client.options.renderDistance().set(6);
        var server = client.getSingleplayerServer();
        var playerId = client.player.getUUID();
        if (phase == 0) {
            phase = 1;
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var level = player.serverLevel();
                if (!(level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource))
                    throw new AssertionError("Use a disposable superflat world for this test");
                for (int dx = -3; dx <= 3; ++dx) for (int dz = -3; dz <= 3; ++dz) level.getChunk(256 + dx, 256 + dz);
                var old = new ArrayList<PaleHoundEntity>();
                for (var entity : level.getAllEntities()) if (entity instanceof PaleHoundEntity hound) old.add(hound);
                old.forEach(PaleHoundEntity::discard);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 4096, 4096);
                player.setGameMode(GameType.CREATIVE);
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.teleportTo(4096.5, ground, 4096.5);
                player.setYRot(0); player.setYHeadRot(0); player.setXRot(0);
                level.setDayTime(18000); level.updateSkyBrightness();
                prepared = true;
            });
            marked = ticks;
        } else if (phase == 1 && prepared && ticks > marked + 60) {
            phase = 2;
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                var level = player.serverLevel();
                var natural = Utils.trySpawnMob(player, ModEntityTypes.PALE_HOUND.get(), net.minecraft.world.entity.MobSpawnType.TRIGGERED,
                    level, player.blockPosition().offset(24, 0, 0), 1, 0, 6,
                    net.minecraft.util.SpawnUtil.Strategy.ON_TOP_OF_COLLIDER).orElseThrow(() -> new AssertionError("Native flat-ground spawn failed"));
                if (natural.getY() != player.getY() || !level.noCollision(natural)) throw new AssertionError("Native spawn placed Hound below/inside flat ground");
                natural.discard();
                try {
                    if (server.getCommands().getDispatcher().execute("pale_hound encounter", player.createCommandSourceStack().withPermission(2)) != 1)
                        throw new AssertionError("Flat-ground encounter command failed");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) { throw new RuntimeException(error); }
                var hound = level.getEntities(ModEntityTypes.PALE_HOUND.get(), h -> h.isCommandEncounterTarget(player)).getFirst();
                if (hound.getY() != player.getY() || !level.noCollision(hound)) throw new AssertionError("Command placed Hound below/inside flat ground");
                hound.setNoAi(true);
                if (!hound.isSurfaceStalking()) {
                    hound.setTarget(null); hound.currentRoll = Roll.STROLL;
                    if (!hound.tryStartSurfaceStalking(player)) throw new AssertionError("Superflat surface was not recognized");
                }

                entityId = hound.getId();
                level.setDayTime(6000); level.updateSkyBrightness();
                PaleHound.LOG.info("SUPERFLAT SPAWN CHECK: native and command Hounds placed correctly at Y={}; observer 48 blocks away", hound.getY());
            });
        } else if (phase == 2 && client.level.getEntity(entityId) instanceof PaleHoundEntity hound && hound.isCrawling()) {
            var camera = EntityType.ARMOR_STAND.create(client.level);
            camera.setPos(hound.getX(), hound.getY() - 0.15, hound.getZ() + 48);
            camera.lookAt(EntityAnchorArgument.Anchor.EYES, hound.position().add(0, 1.3, 0));
            camera.setYHeadRot(camera.getYRot()); camera.yHeadRotO = camera.getYRot();
            camera.yRotO = camera.getYRot(); camera.xRotO = camera.getXRot();
            camera.setOldPosAndRot();
            client.setCameraEntity(camera); // Observe without aiming the server player and triggering its gaze retreat.
            if (!hound.shouldRenderAtSqrDistance(48 * 48)) throw new AssertionError("Crawling Hound is culled at 48 blocks");
            phase = 3; marked = ticks;
        } else if (phase == 3 && ticks > marked + 100) {
            Screenshot.grab(client.gameDirectory, "superflat-crawl-48.png", client.getMainRenderTarget(), message -> captured = true);
            phase = 4;
        } else if (phase == 4 && captured) {
            PaleHound.LOG.info("CLIENT SUPERFLAT SMOKE PASSED: native/command placement on negative-Y terrain and crouched rendering at 48 blocks verified");
            client.setCameraEntity(client.player);
            phase = 5;
            client.stop();
        }
    }
}
