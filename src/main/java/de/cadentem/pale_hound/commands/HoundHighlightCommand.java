package de.cadentem.pale_hound.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.network.HoundHighlight;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HoundHighlightCommand {
    private HoundHighlightCommand() {}

    public static boolean canUse(CommandSourceStack source) {
        return source.hasPermission(2) || source.getEntity() instanceof ServerPlayer player && player.isCreative();
    }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("highlight")
            .requires(source -> source.getEntity() instanceof ServerPlayer && canUse(source))
            .executes(context -> highlight(context.getSource(), 30))
            .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 300))
                .executes(context -> highlight(context.getSource(), IntegerArgumentType.getInteger(context, "seconds"))))
            .then(Commands.literal("off").executes(context -> {
                PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(), new HoundHighlight(List.of(), 0));
                context.getSource().sendSuccess(() -> Component.literal("Pale Hound highlights cleared."), false);
                return 1;
            }));
    }

    private static int highlight(CommandSourceStack source, int seconds) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var level = player.serverLevel();
        var hounds = loadedHounds(level);
        var ids = hounds.stream().map(PaleHoundEntity::getUUID).toList();
        PacketDistributor.sendToPlayer(player, new HoundHighlight(ids, seconds * 20));
        int count = ids.size();
        String message;
        if (count == 0) {
            var elsewhere = new ArrayList<String>();
            for (var other : source.getServer().getAllLevels()) {
                if (other == level) continue;
                int otherCount = loadedHounds(other).size();
                if (otherCount > 0) elsewhere.add(other.dimension().location() + " (" + otherCount + ")");
            }
            message = "No server-loaded Pale Hounds in " + level.dimension().location() + "."
                + (elsewhere.isEmpty() ? " None found in other dimensions." : " Found in: " + String.join(", ", elsewhere) + ".");
        } else {
            var nearest = hounds.stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElseThrow();
            var pos = nearest.blockPosition();
            message = "Highlighted " + count + " Pale Hound(s) for " + seconds + " seconds (visible only to you)."
                + " Nearest: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + " (" + Math.round(player.distanceTo(nearest)) + " blocks).";
        }
        String feedback = message;
        source.sendSuccess(() -> Component.literal(feedback), false);
        return count;
    }

    private static List<PaleHoundEntity> loadedHounds(ServerLevel level) {
        var result = new ArrayList<PaleHoundEntity>();
        level.getEntities(ModEntityTypes.PALE_HOUND.get(), entity -> !entity.isRemoved(), result, HoundHighlight.MAX_ENTITIES);
        return result;
    }
}
