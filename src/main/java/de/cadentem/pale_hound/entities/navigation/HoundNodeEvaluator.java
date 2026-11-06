package de.cadentem.pale_hound.entities.navigation;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.entities.hunting.HoundHazardLearning.Hazard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Add experienced risks to walkable A* nodes without changing walls, doors or unsupported air. */
public final class HoundNodeEvaluator extends WalkNodeEvaluator {
    private final PaleHoundEntity hound;
    public HoundNodeEvaluator(PaleHoundEntity hound) { this.hound = hound; }
    @Override public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        var original = super.getPathType(context, x, y, z);
        var position = new BlockPos(x, y, z); var learning = hound.getHazardLearning();
        if (learning != null && learning.enabled()) {
            if (context.level().getBlockState(position).is(Blocks.COBWEB)) {
                var floor = context.level().getBlockState(position.below());
                if (floor.getCollisionShape(context.level(), position.below()).isEmpty()) return PathType.OPEN;
                return learning.score(Hazard.WEBS) > 0 ? PathType.DANGER_OTHER : PathType.WALKABLE;
            }
            if ((original == PathType.WALKABLE || original == PathType.DANGER_FIRE || original == PathType.DANGER_OTHER || original == PathType.WATER_BORDER)
                && learning.dangerousExplosiveAt(position)) return PathType.DANGER_OTHER;
        }
        return original;
    }
}
