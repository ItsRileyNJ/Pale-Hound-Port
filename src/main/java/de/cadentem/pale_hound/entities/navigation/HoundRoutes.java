package de.cadentem.pale_hound.entities.navigation;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Bounded waypoint search: test up to four complete, safe routes before giving up. */
public final class HoundRoutes {
    private HoundRoutes() {}

    @Nullable
    public static Path findReachable(PaleHoundEntity hound, List<Vec3> candidates) {
        int attempts = 0;
        for (Vec3 candidate : candidates) {
            if (candidate == null) continue;
            if (++attempts > 4) break;
            BlockPos ground = findGround(hound, candidate);
            if (ground == null) continue;
            Path path = hound.getNavigation().createPath(ground, 0);
            if (path != null && path.canReach()) return path;
        }
        return null;
    }

    @Nullable
    private static BlockPos findGround(PaleHoundEntity hound, Vec3 point) {
        BlockPos base = BlockPos.containing(point);
        if (!hound.level().hasChunkAt(base)) return null;
        for (int dy = 2; dy >= -hound.getMaxFallDistance(); --dy) {
            BlockPos feet = base.above(dy);
            var floor = hound.level().getBlockState(feet.below());
            if (!hound.level().getWorldBorder().isWithinBounds(feet)
                || !floor.getFluidState().isEmpty() || floor.getCollisionShape(hound.level(), feet.below()).isEmpty()
                || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS) || floor.is(Blocks.POWDER_SNOW)) continue;
            var state = hound.level().getBlockState(feet);
            if (!state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) continue;
            // Path searches use the Hound's crawling clearance, including low tunnel alternatives.
            AABB clearance = new AABB(feet.getX() + 0.25, feet.getY(), feet.getZ() + 0.25,
                feet.getX() + 0.75, feet.getY() + 0.5, feet.getZ() + 0.75);
            if (hound.level().noCollision(hound, clearance)) return feet;
        }
        return null;
    }
}
