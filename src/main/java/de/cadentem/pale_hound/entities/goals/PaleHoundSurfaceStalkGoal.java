package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import java.util.EnumSet;
import java.util.ArrayList;
import de.cadentem.pale_hound.entities.navigation.HoundRoutes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Follow a surface player without closing to melee range. */
public final class PaleHoundSurfaceStalkGoal extends Goal {
    private final PaleHoundEntity hound;
    private int repathDelay;
    private boolean wasRetreating;
    private final de.cadentem.pale_hound.entities.navigation.HoundRetreat backstep = new de.cadentem.pale_hound.entities.navigation.HoundRetreat();
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
        hound.setBackingAway(false);
        backstep.reset();
        hound.pleaseStopMoving = false;
        hound.setCrawling(!hound.isSurfaceStalkRetreating());
    }

    @Override public void tick() {
        var player = hound.getSurfaceStalkTarget();
        if (player == null) return;
        hound.setAggressive(false);
        boolean retreating = hound.isSurfaceStalkRetreating();
        if (retreating && !wasRetreating) {
            hound.setBackingAway(false);
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
        if (!hound.isBackingAway() && distance < 28 * 28) {
            hound.setBackingAway(true);
            backstep.reset();
            repathDelay = 0;
            hound.getNavigation().stop();
        } else if (hound.isBackingAway() && distance >= 36 * 36) {
            hound.setBackingAway(false);
            hound.getNavigation().stop();
            repathDelay = 0;
        }
        if (hound.isBackingAway()) {
            // Keep a completed, outward route. The movement control walks it backwards.
            if (hound.getNavigation().isDone() && --repathDelay <= 0) {
                repathDelay = 10;
                var route = backstep.findPath(hound, player.position(), true);
                if (route != null) hound.getNavigation().moveTo(route, 0.22);
            }
            return;
        }
        if (distance <= 48 * 48) {
            hound.getNavigation().stop();
            repathDelay = 0;
            Vec3 toward = player.position().subtract(hound.position());
            float facing = (float)Math.toDegrees(Math.atan2(-toward.x, toward.z));
            hound.setYRot(net.minecraft.util.Mth.approachDegrees(hound.getYRot(), facing, 6));
            hound.setYBodyRot(hound.getYRot());
            return;
        }
        if (--repathDelay > 0) return;
        repathDelay = 20;
        Vec3 away = hound.position().subtract(player.position()).multiply(1, 0, 1).normalize();
        if (away.lengthSqr() < 0.001) away = new Vec3(1, 0, 0);
        var candidates = new ArrayList<Vec3>();
        candidates.add(player.position().add(away.scale(40)));
        candidates.add(player.position().add(away.yRot((float)(Math.PI / 3)).scale(40)));
        candidates.add(player.position().add(away.yRot((float)(-Math.PI / 3)).scale(40)));
        candidates.add(player.position().add(away.yRot((float)(Math.PI / 2)).scale(40)));
        var route = HoundRoutes.findReachable(hound, candidates);
        if (route != null) hound.getNavigation().moveTo(route, 0.35);
    }

    @Override public void stop() {
        hound.getNavigation().stop();
        hound.setBackingAway(false);
        hound.setCrawling(false);
    }
}
