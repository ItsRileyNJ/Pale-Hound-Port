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

    @Override protected net.minecraft.world.level.pathfinder.PathFinder createPathFinder(int nodes) {
        this.nodeEvaluator = new HoundNodeEvaluator((PaleHoundEntity)this.mob);
        this.nodeEvaluator.setCanPassDoors(true);
        return new net.minecraft.world.level.pathfinder.PathFinder(this.nodeEvaluator, nodes);
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
        if (!isDone() || hound.isClimbing()) {
            super.tick();
        } else if (((ClimberNavigationAccessor)(Object)this).pale_hound$getClimbTarget() != null) {
            // Spider navigation otherwise walks straight at a stale destination after a
            // partial path ends, even while crawling makes climbing impossible.
            hound.getMoveControl().setWantedPosition(hound.getX(), hound.getY(), hound.getZ(), 0);
        }
        if (this.path != null && !this.path.isDone()) {
            // Narrow-cave navigation visits every block center. On open ground that
            // creates a left/right staircase; skip it only across fully clear ground.
            for (int index = Math.min(this.path.getNodeCount() - 1, this.path.getNextNodeIndex() + 32);
                 index > this.path.getNextNodeIndex(); --index) {
                Vec3 waypoint = this.path.getEntityPosAtNode(hound, index);
                if (!HoundRoutes.canWalkStraight(hound, waypoint)) continue;
                this.path.setNextNodeIndex(index);
                hound.getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, this.speedModifier);
                break;
            }
        }
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
        if (replacement != null && (replacement.canReach() || hound.isClimbing())) moveTo(replacement, this.speedModifier);
        else stop();
    }

    @Override protected void followThePath() {
        // Reach the center of each waypoint before turning. Vanilla's generous arrival
        // radius and early corner skipping can steer the body into a narrow cave corner.
        Vec3 next = this.path.getNextEntityPos(hound);
        this.maxDistanceToWaypoint = 0.2F;
        if (Math.abs(hound.getX() - next.x) < 0.2 && Math.abs(hound.getZ() - next.z) < 0.2
            && Math.abs(hound.getY() - next.y) < 1.0) this.path.advance();
        this.doStuckDetection(this.getTempMobPos());
    }
}
