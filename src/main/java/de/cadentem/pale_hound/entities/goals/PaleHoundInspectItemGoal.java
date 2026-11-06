package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.entities.hunting.HoundHuntBrain;
import de.cadentem.pale_hound.entities.navigation.HoundRoutes;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Brief curiosity with a complete approach, immediate sound interruption and a progress deadline. */
public final class PaleHoundInspectItemGoal extends Goal {
    private final PaleHoundEntity hound;
    private ItemEntity item;
    private Path approach;
    private boolean active;
    private int nextCheck, deadline, lookUntil, progressAt;
    private Vec3 progressPosition;

    public PaleHoundInspectItemGoal(PaleHoundEntity hound) {
        this.hound = hound;
        nextCheck = 100 + hound.getRandom().nextInt(101);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    public boolean isActive() { return active; }
    private boolean calm() {
        if (!hound.getHuntBrain().enabled() || hound.getHuntBrain().activity() != HoundHuntBrain.Activity.NONE
            || hound.isNoAi() || hound.isAggressive() || hound.getTarget() != null || hound.isFleeing
            || hound.isSurfaceStalkRetreating() || hound.isAvoidingHazard() || hound.isBackingAway()
            || hound.currentRoll != Roll.STROLL || hound.isInWaterOrBubble() || !hound.onGround()) return false;
        // Curiosity must not draw a stalker toward a player or delay close-range reactions.
        return hound.level().players().stream().noneMatch(player -> hound.isValidEncounterTarget(player)
            && hound.distanceToSqr(player) < 24 * 24 && hound.hasLineOfSight(player));
    }
    private boolean visible(ItemEntity candidate) {
        if (candidate == null || candidate.isRemoved() || candidate.getItem().isEmpty() || !candidate.onGround()
            || hound.distanceToSqr(candidate) > 12 * 12 || Math.abs(candidate.getY() - hound.getY()) > 2) return false;
        return hound.level().clip(new ClipContext(hound.getEyePosition(), candidate.position().add(0, 0.15, 0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hound)).getType() == HitResult.Type.MISS;
    }
    @Override public boolean canUse() {
        if (!calm()) return false;
        if (item != null && !active && visible(item)) return true;
        if (hound.tickCount < nextCheck) return false;
        nextCheck = hound.tickCount + 100 + hound.getRandom().nextInt(101);
        if (hound.getRandom().nextFloat() >= 0.35F) return false;
        var items = hound.level().getEntitiesOfClass(ItemEntity.class, hound.getBoundingBox().inflate(12), this::visible);
        items.sort(Comparator.comparingDouble(hound::distanceToSqr));
        int attempts = 0;
        for (var candidate : items) {
            if (++attempts > 4) break;
            var player = hound.getSurfaceStalkTarget();
            if (player != null && candidate.distanceToSqr(player) < 24 * 24) continue;
            var route = HoundRoutes.findReachable(hound, List.of(candidate.position()));
            if (route == null || route.getNodeCount() > 32
                || Vec3.atBottomCenterOf(route.getTarget()).distanceToSqr(candidate.position()) > 4) continue;
            item = candidate; approach = route;
            return true;
        }
        return false;
    }
    @Override public boolean canContinueToUse() {
        return active && calm() && visible(item) && hound.tickCount < deadline
            && (lookUntil == 0 || hound.tickCount < lookUntil);
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        active = true; lookUntil = 0; deadline = hound.tickCount + 160;
        progressAt = hound.tickCount; progressPosition = hound.position();
        hound.pleaseStopMoving = false; hound.setBackingAway(false); hound.setCrawling(true);
        hound.getNavigation().moveTo(approach, 0.35);
    }
    @Override public void tick() {
        if (!canContinueToUse()) { cancel(); return; }
        if (hound.distanceToSqr(item) <= 2.5 * 2.5) {
            if (lookUntil == 0) {
                lookUntil = hound.tickCount + 40 + hound.getRandom().nextInt(21);
                hound.getNavigation().stop();
            }
            hound.setInspectingGround(true);
            hound.getLookControl().setLookAt(item.getX(), item.getY() + 0.1, item.getZ(), 25, 25);
            return;
        }
        // Never keep staring at an inaccessible item, or chase one that is being moved around.
        if (lookUntil != 0 || hound.getNavigation().isDone()) { cancel(); return; }
        if (hound.position().distanceToSqr(progressPosition) >= 0.16) {
            progressPosition = hound.position(); progressAt = hound.tickCount;
        } else if (hound.tickCount - progressAt >= 20) { cancel(); return; }
        var path = hound.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            var next = path.getNextEntityPos(hound);
            hound.getLookControl().setLookAt(next.x, next.y + hound.getEyeHeight(), next.z);
        }
    }
    public void cancel() {
        if (!active && item == null) return;
        active = false; item = null; approach = null;
        nextCheck = hound.tickCount + 600 + hound.getRandom().nextInt(401);
        hound.setInspectingGround(false); hound.getNavigation().stop();
        hound.resumeRoaming();
    }
    @Override public void stop() { cancel(); }
}
