package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.entities.navigation.HoundRetreat;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Temporary caution and withdrawal, not a timed encounter despawn or immunity. */
public final class PaleHoundHazardAvoidGoal extends Goal {
    private final PaleHoundEntity hound;
    private final HoundRetreat escape = new HoundRetreat();
    private Vec3 threat;
    private int linger, repath;
    public PaleHoundHazardAvoidGoal(PaleHoundEntity hound) { this.hound = hound; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    @Override public boolean canUse() {
        if (hound.isSurfaceStalkRetreating() || hound.isFleeing || hound.currentRoll == Roll.FLEE) return false;
        threat = hound.getHazardLearning().nearestThreat(); return threat != null;
    }
    @Override public boolean canContinueToUse() {
        if (hound.isSurfaceStalkRetreating() || hound.isFleeing || hound.currentRoll == Roll.FLEE) return false;
        var current = hound.getHazardLearning().nearestThreat();
        if (current != null) { threat = current; linger = 20; }
        return current != null || linger > 0;
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        hound.getHazardLearning().setAvoiding(true); hound.setBackingAway(false); hound.pleaseStopMoving = false;
        hound.getNavigation().stop(); escape.reset(); linger = 20; repath = 0;
    }
    @Override public void stop() { hound.getHazardLearning().setAvoiding(false); hound.getNavigation().stop(); }
    @Override public void tick() {
        --linger;
        if (hound.getNavigation().isDone() && --repath <= 0) {
            repath = 10; var path = escape.findPath(hound, threat);
            if (path != null) hound.getNavigation().moveTo(path, 1.1);
        }
        var path = hound.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            var point = path.getNextEntityPos(hound);
            hound.getLookControl().setLookAt(point.x, point.y + hound.getEyeHeight(), point.z);
        }
    }
}
