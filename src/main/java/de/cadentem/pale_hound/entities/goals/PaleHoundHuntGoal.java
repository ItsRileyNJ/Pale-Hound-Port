package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.entities.hunting.HoundHuntBrain;
import de.cadentem.pale_hound.entities.navigation.HoundRoutes;
import java.util.ArrayList;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/** Inspect the last evidence, then pause and check reachable nearby exits/corners. */
public final class PaleHoundHuntGoal extends Goal {
    private final PaleHoundEntity hound;
    private int revision = -1, repath, pause;
    private Vec3 destination;
    private Vec3 progressPosition, inspectionPoint;
    private int progressAt;
    private boolean first;
    private int nextGlance, glanceUntil;
    private double glanceYaw;
    private boolean inspectingEvidence;
    public PaleHoundHuntGoal(PaleHoundEntity hound) {
        this.hound = hound; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    private HoundHuntBrain brain() { return hound.getHuntBrain(); }
    @Override public boolean canUse() { return brain().controlsMovement(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { hound.getNavigation().setMaxVisitedNodesMultiplier(de.cadentem.pale_hound.config.ServerConfig.HUNT_PATH_BUDGET.get().floatValue()); revision = -1; repath = 0; pause = 0; destination = null; hound.pleaseStopMoving = false; }
    @Override public void stop() { hound.getNavigation().stop(); hound.getNavigation().setMaxVisitedNodesMultiplier(6.0F); hound.setInspectingGround(false); hound.setCrawling(false); }
    @Override public void tick() {
        hound.setAggressive(brain().isAngryTrail());
        if (revision != brain().revision()) {
            revision = brain().revision(); first = true; pause = 0; repath = 0; destination = null; hound.getNavigation().stop();
            inspectingEvidence = false; hound.setInspectingGround(false);
        }
        if (brain().evidencePosition() == null) return;
        if (pause > 0) {
            --pause;
            hound.setInspectingGround(inspectingEvidence);
            if (hound.tickCount >= nextGlance && hound.getRandom().nextFloat() < 0.12F) {
                glanceYaw = hound.getYRot() + (hound.getRandom().nextBoolean() ? 50 : -50);
                glanceUntil = hound.tickCount + 12 + hound.getRandom().nextInt(13);
                nextGlance = hound.tickCount + 60 + hound.getRandom().nextInt(61);
            }
            if (hound.tickCount < glanceUntil) {
                double angle=Math.toRadians(glanceYaw);
                hound.getLookControl().setLookAt(hound.getX()-Math.sin(angle)*5, hound.getEyeY(), hound.getZ()+Math.cos(angle)*5, 6, 6);
                return;
            }
            if (inspectingEvidence) {
                var point = inspectionPoint;
                hound.getLookControl().setLookAt(point.x, point.y + 0.1, point.z, 25, 25);
                return;
            }
            double yaw = Math.toRadians(hound.getYRot() + (pause > 15 ? -55 : 55));
            hound.getLookControl().setLookAt(hound.getX() - Math.sin(yaw) * 6, hound.getEyeY(), hound.getZ() + Math.cos(yaw) * 6);
            return;
        }
        hound.setInspectingGround(false); inspectingEvidence = false;
        if (destination != null) {
            if (hound.distanceToSqr(destination) <= 2.25) {
                inspectingEvidence = first && hound.level().clip(new net.minecraft.world.level.ClipContext(
                    hound.getEyePosition(), destination.add(0, 0.1, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, hound)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
                inspectionPoint = destination;
                hound.setInspectingGround(inspectingEvidence);
                brain().reached(destination); destination = null; pause = 25 + hound.getRandom().nextInt(26); first = false;
                hound.getNavigation().stop(); return;
            }
            if (hound.position().distanceToSqr(progressPosition) >= 0.16) {
                progressPosition = hound.position(); progressAt = hound.tickCount;
            }
            if (hound.getNavigation().isDone() || hound.tickCount - progressAt >= 20) {
                // An abandoned path is not an arrival: try a different route without a staring pause.
                hound.getNavigation().stop(); destination = null; first = false; repath = 0; return;
            }
            var path = hound.getNavigation().getPath();
            if (path != null && !path.isDone()) {
                Vec3 next = path.getNextEntityPos(hound);
                hound.getLookControl().setLookAt(next.x, next.y + hound.getEyeHeight(), next.z);
            }
            return;
        }
        if (--repath > 0) return;
        repath = 15;
        var candidates = new ArrayList<Vec3>();
        if (first) candidates.add(brain().evidencePosition());
        Vec3 direction = brain().travelDirection();
        if (direction.lengthSqr() < 0.01) direction = Vec3.directionFromRotation(0, hound.getYRot());
        float turn = (float)((brain().legs() * 1.7 + hound.getRandom().nextDouble() * 0.7) - 1.0);
        for (int i = 0; i < (first ? 3 : 4); ++i) {
            double distance = 5 + hound.getRandom().nextInt(10);
            Vec3 point = brain().evidencePosition().add(direction.yRot(turn + i * 1.4F).scale(distance));
            if (!brain().alreadySearched(point)) candidates.add(point);
        }
        hound.setCrawling(brain().shouldCrawl());
        var path = HoundRoutes.findReachable(hound, candidates);
        if (path == null) { first = false; pause = 15; return; }
        destination = Vec3.atBottomCenterOf(path.getTarget());
        progressPosition = hound.position(); progressAt = hound.tickCount;
        hound.getNavigation().moveTo(path, brain().speed());
    }
}
