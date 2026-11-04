package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import java.util.EnumSet;
import java.util.ArrayList;
import de.cadentem.pale_hound.entities.navigation.HoundRoutes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/** Follow a surface player without closing to melee range. */
public final class PaleHoundSurfaceStalkGoal extends Goal {
    private final PaleHoundEntity hound;
    private int repathDelay;
    private boolean wasRetreating;
    private final de.cadentem.pale_hound.entities.navigation.HoundRetreat escape = new de.cadentem.pale_hound.entities.navigation.HoundRetreat();

    public PaleHoundSurfaceStalkGoal(PaleHoundEntity hound) {
        this.hound = hound;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() { return hound.isSurfaceStalking(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void start() {
        repathDelay = 0;
        wasRetreating = false;
        hound.pleaseStopMoving = false;
        hound.setCrawling(!hound.isSurfaceStalkRetreating());
    }

    @Override public void tick() {
        var player = hound.getSurfaceStalkTarget();
        if (player == null) return;
        hound.setAggressive(false);
        boolean retreating = hound.isSurfaceStalkRetreating();
        if (retreating && !wasRetreating) {
            repathDelay = 0;
            escape.reset();
            hound.getNavigation().stop();
            hound.playFleeSound();
        }
        wasRetreating = retreating;
        if (retreating) {
            var navigation = hound.getNavigation();
            if (navigation.isDone() && --repathDelay <= 0) {
                repathDelay = 10;
                var route = escape.findPath(hound, player.position());
                if (route != null) navigation.moveTo(route, 1.2);
            }
            var route = navigation.getPath();
            if (route != null && !route.isDone()) {
                var next = route.getNextEntityPos(hound);
                hound.getLookControl().setLookAt(next.x, next.y + hound.getEyeHeight(), next.z);
            }
            return;
        }
        hound.setCrawling(true);
        hound.getLookControl().setLookAt(player, 30, 30);
        double distance = hound.distanceToSqr(player);
        if (distance >= 16 * 16 && distance <= 24 * 24) {
            hound.getNavigation().stop();
            repathDelay = 0;
            return;
        }
        if (--repathDelay > 0) return;
        repathDelay = 20;
        Vec3 away = hound.position().subtract(player.position()).multiply(1, 0, 1).normalize();
        if (away.lengthSqr() < 0.001) away = new Vec3(1, 0, 0);
        Vec3 fallback = hound.position().add(away.scale(12));
        boolean backingOff = distance < 16 * 16;
        Vec3 destination = backingOff
            ? DefaultRandomPos.getPosAway(hound, 16, 7, player.position())
            : player.position().add(away.scale(20));
        if (destination == null) destination = fallback;
        double speed = 0.35;
        var candidates = new ArrayList<Vec3>();
        candidates.add(destination);
        // Try flanking routes along the same standoff ring rather than pressing into one obstacle.
        double radius = backingOff ? 12 : 20;
        Vec3 center = backingOff ? hound.position() : player.position();
        candidates.add(center.add(away.yRot((float)(Math.PI / 3)).scale(radius)));
        candidates.add(center.add(away.yRot((float)(-Math.PI / 3)).scale(radius)));
        candidates.add(backingOff ? fallback : center.add(away.yRot((float)(Math.PI / 2)).scale(radius)));
        var route = HoundRoutes.findReachable(hound, candidates);
        boolean hasPath = route != null && hound.getNavigation().moveTo(route, speed);
        if (!hasPath && backingOff) {
            destination = fallback;
            hound.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
        }
    }

    @Override public void stop() {
        hound.getNavigation().stop();
        hound.setCrawling(false);
    }
}
