package de.cadentem.pale_hound.commands;

import de.cadentem.pale_hound.registry.ModEntityTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class HoundInspectCommand {
    private HoundInspectCommand() {}
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("inspect").requires(HoundHighlightCommand::canUse).executes(context -> {
            var source = context.getSource();
            var nearest = source.getLevel().getEntities(ModEntityTypes.PALE_HOUND.get(), entity -> !entity.isRemoved()).stream()
                .min(java.util.Comparator.comparingDouble(entity -> entity.position().distanceToSqr(source.getPosition()))).orElse(null);
            if (nearest == null) { source.sendFailure(Component.literal("No loaded Pale Hounds in this dimension.")); return 0; }
            var brain = nearest.getHuntBrain();
            source.sendSuccess(() -> Component.literal("Pale Hound: " + brain.phase() + "; suspicion " + Math.round(brain.suspicion())
                + "/100; last seen " + position(brain.lastSeenPosition()) + "; last heard " + position(brain.lastHeardPosition())
                + "; investigation " + position(brain.evidencePosition())
                + "; learned hazards " + nearest.getHazardLearning().describe() + (nearest.isNoAi() ? "; AI disabled" : "")), false);
            return 1;
        });
    }
    private static String position(Vec3 position) {
        if (position == null) return "none";
        var block = net.minecraft.core.BlockPos.containing(position);
        return block.getX() + ", " + block.getY() + ", " + block.getZ();
    }
}
