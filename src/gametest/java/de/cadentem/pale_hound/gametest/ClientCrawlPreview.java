package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Optional visual development fixture: run only in a disposable test world. */
@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class ClientCrawlPreview {
    private static boolean started;
    private static volatile int entityId = -1;
    private static int clientTicks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("pale_hound.clientCrawlPreview")) return;
        var client = Minecraft.getInstance();
        if (started) {
            if (client.level != null && client.level.getEntity(entityId) instanceof PaleHoundEntity hound) {
                boolean walking = clientTicks++ % 200 < 100;
                hound.walkAnimation.update(walking ? 0.8F : 0, 1);
                hound.setDeltaMovement(walking ? new Vec3(0.02, 0, 0) : Vec3.ZERO);
            }
            return;
        }
        if (client.player == null || client.getSingleplayerServer() == null) return;
        started = true;
        client.options.pauseOnLostFocus = false;
        var uuid = client.player.getUUID();
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            var level = player.serverLevel();
            level.setDayTime(6000);
            int x = player.blockPosition().getX() + 20;
            int z = player.blockPosition().getZ();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Vec3 origin = new Vec3(x + 0.5, y, z + 0.5);
            var hound = new PaleHoundEntity(ModEntityTypes.PALE_HOUND.get(), level) {
                private int previewTick;
                @Override public void tick() {
                    super.tick();
                    setCrawling(true);
                    int t = previewTick++ % 200;
                    setPos(origin.add(Math.min(t, 100) * 0.01, 0, 0));
                    setYRot(-90);
                    setYHeadRot(-90 + (float) Math.sin(previewTick / 30.0) * 35);
                    setXRot((float) Math.sin(previewTick / 45.0) * 20);
                    this.yBodyRot = -90;
                    setDeltaMovement(t < 100 ? new Vec3(0.01, 0, 0) : Vec3.ZERO);
                }
            };
            hound.setNoAi(true);
            hound.setNoGravity(true);
            hound.setPos(origin);
            level.addFreshEntity(hound);
            entityId = hound.getId();
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(origin.x + 2.5, origin.y - 0.6, origin.z + 3.5);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, origin.add(0.5, 0.5, 0));
        });
    }
}
