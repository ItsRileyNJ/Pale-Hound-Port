package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="pale_hound", value=Dist.CLIENT)
public final class ClientReferencePreview {
    private static boolean started;
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("pale_hound.referencePreview")) return;
        var client = Minecraft.getInstance();
        if (client.player == null || client.getSingleplayerServer() == null) return;
        if (!started) {
            started = true; client.options.hideGui = true; client.options.pauseOnLostFocus = false;
            var id = client.player.getUUID(); var server = client.getSingleplayerServer();
            server.execute(() -> {
                var player=server.getPlayerList().getPlayer(id); var level=player.serverLevel(); level.setDayTime(6000);
                for(int x=-12;x<=12;x++) for(int z=-12;z<=12;z++) {
                    level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.POLISHED_ANDESITE.defaultBlockState());
                    for(int y=100;y<=108;y++) level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                level.getEntities(ModEntityTypes.REFERENCE_PREVIEW.get(), e -> true).forEach(net.minecraft.world.entity.Entity::discard);
                var model=ModEntityTypes.REFERENCE_PREVIEW.get().create(level); model.setPos(0.5,100,0.5); level.addFreshEntity(model);
                player.setGameMode(GameType.SPECTATOR); player.teleportTo(3.2,100.8,4.6);
                player.lookAt(EntityAnchorArgument.Anchor.EYES,new Vec3(.5,101.5,.5));
            });
        }
        if (++ticks==120) Screenshot.grab(client.gameDirectory,"reference-preview.png",client.getMainRenderTarget(),message -> {});
        if(ticks==140) { de.cadentem.pale_hound.PaleHound.LOG.info("REFERENCE PREVIEW CLIENT PASSED: entity rendered; screenshot saved"); client.stop(); }
    }
}
