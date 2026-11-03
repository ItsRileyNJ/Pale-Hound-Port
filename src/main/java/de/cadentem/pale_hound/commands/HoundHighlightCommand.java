package de.cadentem.pale_hound.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.network.HoundHighlight;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
        var ids = new ArrayList<UUID>();
        for (var entity : source.getLevel().getAllEntities()) {
            if (entity instanceof PaleHoundEntity && entity.isAlive()) ids.add(entity.getUUID());
            if (ids.size() >= HoundHighlight.MAX_ENTITIES) break;
        }
        PacketDistributor.sendToPlayer(source.getPlayerOrException(), new HoundHighlight(ids, seconds * 20));
        int count = ids.size();
        source.sendSuccess(() -> Component.literal(count == 0 ? "No loaded Pale Hounds found in this dimension."
            : "Highlighted " + count + " Pale Hound(s) for " + seconds + " seconds (visible only to you)."), false);
        return count;
    }
}
