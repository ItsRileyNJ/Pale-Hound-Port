package de.cadentem.pale_hound.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.entities.navigation.HoundRoutes;
import de.cadentem.pale_hound.network.HoundSoundCue;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Starts an encounter directly, without touching either scheduled timer. */
public final class HoundEncounterCommand {
    private HoundEncounterCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("encounter").requires(source -> source.hasPermission(2))
            .executes(context -> start(context.getSource(), context.getSource().getPlayerOrException()))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> start(context.getSource(), EntityArgument.getPlayer(context, "player"))));
    }

    private static int start(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException {
        var level = player.serverLevel();
        var hound = ModEntityTypes.PALE_HOUND.get().create(level);
        if (hound == null) { source.sendFailure(Component.literal("Could not create a Pale Hound.")); return 0; }
        hound.markCommandEncounter(player);
        hound.setCrawling(true);
        Vec3 location = findLocation(hound, player);
        if (location == null) {
            hound.discard();
            source.sendFailure(Component.literal("No collision-free encounter location nearby. Move into a more open area."));
            return 0;
        }
        hound.setPos(location);
        hound.setCrawling(false);
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(hound, level,
            level.getCurrentDifficultyAt(hound.blockPosition()), MobSpawnType.TRIGGERED, null);
        if (!level.addFreshEntity(hound)) {
            hound.discard();
            source.sendFailure(Component.literal("The server prevented the encounter from spawning."));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, new HoundSoundCue(HoundSoundCue.Kind.AMBIENT_CRYING, hound.getUUID()));
        hound.startCommandEncounter(player);
        var pos = hound.blockPosition();
        source.sendSuccess(() -> Component.literal("Started a Pale Hound encounter for " + player.getName().getString()
            + " at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ". Scheduled spawn/noise timers were not reset."), false);
        return 1;
    }

    @Nullable private static Vec3 findLocation(PaleHoundEntity hound, ServerPlayer player) {
        Vec3 behind = Vec3.directionFromRotation(0, player.getYRot()).scale(-1);
        for (int radius : new int[]{28, 20, 12}) for (int i = 0; i < 16; ++i) {
            Vec3 candidate = player.position().add(behind.yRot((float)(i * Math.PI / 8)).scale(radius));
            var ground = HoundRoutes.findGround(hound, candidate);
            if (ground != null) return Vec3.atBottomCenterOf(ground);
        }
        // Flying players can still start one: use clear nearby air if no loaded ground is in range.
        for (int i = 0; i < 8; ++i) {
            Vec3 candidate = player.position().add(behind.yRot((float)(i * Math.PI / 4)).scale(8));
            var bounds = hound.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(candidate);
            if (player.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(candidate))
                && player.serverLevel().getWorldBorder().isWithinBounds(bounds)
                && player.serverLevel().noCollision(hound, bounds)) return candidate;
        }
        return null;
    }
}
