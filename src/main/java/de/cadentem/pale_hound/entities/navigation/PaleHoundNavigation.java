package de.cadentem.pale_hound.entities.navigation;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.mixin.ClimberNavigationAccessor;
import de.cadentem.pale_hound.mixin.NavigationStateAccessor;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Preserve climbing while refreshing a blocked route after two seconds without progress. */
public final class PaleHoundNavigation extends WallClimberNavigation {
    private final PaleHoundEntity hound;
    private Vec3 progressPosition;
    private int stationaryTicks;

    public PaleHoundNavigation(PaleHoundEntity hound, Level level) {
        super(hound, level);
        this.hound = hound;
        this.progressPosition = hound.position();
        setMaxVisitedNodesMultiplier(6.0F);
        setCanFloat(true);
        setCanPassDoors(true);
    }

    @Override public Path createPath(BlockPos position, int accuracy) {
        var accessor = (ClimberNavigationAccessor)(Object)this;
        BlockPos previous = accessor.pale_hound$getClimbTarget();
        var state = (NavigationStateAccessor)(Object)this;
        BlockPos oldTarget = state.pale_hound$getTargetPos();
        int oldRange = state.pale_hound$getReachRange();
        try { return super.createPath(position, accuracy); }
        finally {
            accessor.pale_hound$setClimbTarget(previous);
            state.pale_hound$setTargetPos(oldTarget);
            state.pale_hound$setReachRange(oldRange);
        }
    }

    @Override public Path createPath(Entity entity, int accuracy) {
        var accessor = (ClimberNavigationAccessor)(Object)this;
        BlockPos previous = accessor.pale_hound$getClimbTarget();
        var state = (NavigationStateAccessor)(Object)this;
        BlockPos oldTarget = state.pale_hound$getTargetPos();
        int oldRange = state.pale_hound$getReachRange();
        try { return super.createPath(entity, accuracy); }
        finally {
            accessor.pale_hound$setClimbTarget(previous);
            state.pale_hound$setTargetPos(oldTarget);
            state.pale_hound$setReachRange(oldRange);
        }
    }

    @Override public boolean moveTo(Path path, double speed) {
        boolean started = super.moveTo(path, speed);
        ((ClimberNavigationAccessor)(Object)this).pale_hound$setClimbTarget(started ? path.getTarget() : null);
        var state = (NavigationStateAccessor)(Object)this;
        state.pale_hound$setTargetPos(started ? path.getTarget() : null);
        state.pale_hound$setReachRange(0);
        this.hasDelayedRecomputation = false;
        return started;
    }

    @Override public void stop() {
        super.stop();
        ((ClimberNavigationAccessor)(Object)this).pale_hound$setClimbTarget(null);
        ((NavigationStateAccessor)(Object)this).pale_hound$setTargetPos(null);
        this.hasDelayedRecomputation = false;
        stationaryTicks = 0;
    }

    @Override public void tick() {
        super.tick();
        var target = this.path != null && !this.path.isDone() ? this.path.getTarget()
            : ((ClimberNavigationAccessor)(Object)this).pale_hound$getClimbTarget();
        if (target == null || hound.pleaseStopMoving || hound.position().distanceToSqr(progressPosition) >= 0.25) {
            progressPosition = hound.position();
            stationaryTicks = 0;
            return;
        }
        if (++stationaryTicks < 40) return;
        stationaryTicks = 0;
        // Drop the old cache so a changed obstacle can produce a genuinely new path.
        this.path = null;
        var replacement = createPath(target, 0);
        if (replacement != null) moveTo(replacement, this.speedModifier);
    }
}
