package de.cadentem.pale_hound.entities.hunting;

import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.entities.goals.Roll;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Evidence is a snapshot. Hidden players' current coordinates never drive investigation paths. */
public final class HoundHuntBrain {
    public enum Activity { NONE, INVESTIGATING, SEARCHING, RECOVERING }
    private final PaleHoundEntity hound;
    private Activity activity = Activity.NONE;
    private Vec3 lastSeen, lastHeard, evidence;
    private Vec3 travelDirection = Vec3.ZERO;
    private UUID seenPlayer, heardPlayer;
    private int seenAt, heardAt = -1000, evidenceAt, beganAt, deadline, recoveryUntil;
    private int revision, legs;
    private float suspicion, lastStrength;
    private boolean angry, atEvidence;
    private final Deque<BlockPos> searched = new ArrayDeque<>();
    private final Deque<BlockPos> noisePlaces = new ArrayDeque<>();

    public HoundHuntBrain(PaleHoundEntity hound) { this.hound = hound; }
    public boolean enabled() { return ServerConfig.SENSORY_HUNTING.get(); }
    public Activity activity() { return activity; }
    public static double hearingRange(double radius) { return radius * 1.5 * ServerConfig.HOUND_HEARING_MULTIPLIER.get(); }
    public float suspicion() { return suspicion; }
    public Vec3 lastSeenPosition() { return lastSeen; }
    public Vec3 lastHeardPosition() { return lastHeard; }
    public Vec3 evidencePosition() { return evidence; }
    public Vec3 travelDirection() { return travelDirection; }
    public int revision() { return revision; }
    public int legs() { return legs; }
    public boolean controlsMovement() { return enabled() && (activity == Activity.INVESTIGATING || activity == Activity.SEARCHING); }
    public boolean blocksLegacyTargeting() { return enabled() && activity != Activity.NONE; }
    public boolean shouldCrawl() { return controlsMovement() && (!angry || atEvidence); }
    public boolean isAngryTrail() { return controlsMovement() && angry && !atEvidence; }
    public double speed() { return isAngryTrail() ? 0.85 : 0.30; }
    public String phase() {
        if (hound.isAvoidingHazard()) return "AVOIDING_HAZARD";
        if (hound.isInspectingItem()) return "INSPECTING_ITEM";
        if (activity != Activity.NONE) return activity.name();
        if (hound.isSurfaceStalkRetreating() || hound.isFleeing || hound.currentRoll == Roll.FLEE) return "RETREATING";
        if (hound.isAggressive() && hound.getTarget() != null) return "PURSUING";
        if (hound.isSurfaceStalking()) return "STALKING";
        return "ROAMING";
    }

    /** Focused targets still need sight; crouching reduces discovery distance, not an established clear view. */
    public boolean canSee(Player player, boolean focused) {
        if (!hound.isValidEncounterTarget(player) || player.isRemoved() || player.level() != hound.level()) return false;
        if (!enabled()) return true;
        double distance = hound.distanceToSqr(player);
        double range = visionRange(player, focused);
        if (distance > range * range || !hound.hasLineOfSight(player)) return false;
        if (focused || distance <= 12 * 12) return true;
        Vec3 toward = player.position().subtract(hound.position()).multiply(1, 0, 1).normalize();
        Vec3 facing = Vec3.directionFromRotation(0, hound.getYHeadRot());
        return toward.dot(facing) >= Math.cos(Math.toRadians(player.isCrouching() && !HoundLightAwareness.isIlluminated(player) && !this.recentlyHeard(player) ? 50 : 70));
    }

    public double visionRange(Player player, boolean focused) {
        double range = ServerConfig.HUNT_VISION_RANGE.get();
        boolean lit = HoundLightAwareness.isIlluminated(player);
        if (lit) range = Math.max(range, ServerConfig.HUNT_LIT_VISION_RANGE.get());
        else if (!focused && player.isCrouching() && !this.recentlyHeard(player)) range *= player.getDeltaMovement().horizontalDistanceSqr() < 0.001 ? 0.35 : 0.5;
        return range;
    }

    private boolean recentlyHeard(Player player) { return player.getUUID().equals(this.heardPlayer) && hound.tickCount - this.heardAt <= 40; }

    public void observe(Player player) {
        if (!hound.isValidEncounterTarget(player) || player.isRemoved() || !hound.hasLineOfSight(player)) return;
        Vec3 position = player.position();
        if (lastSeen != null && player.getUUID().equals(seenPlayer)) {
            Vec3 delta = position.subtract(lastSeen).multiply(1, 0, 1);
            if (delta.lengthSqr() > 0.01) travelDirection = delta.normalize();
        }
        lastSeen = position; seenAt = hound.tickCount; seenPlayer = player.getUUID();
        suspicion = Math.max(suspicion, hound.isAggressive() || hound.currentRoll == Roll.CHASE ? 80 : 20);
    }

    /** A world noise can reveal an area and raise suspicion, but cannot reveal an unseen moving target. */
    public boolean hear(Player source, Vec3 position, double radius, float strength) {
        if (!enabled() || source != null && (!hound.isValidEncounterTarget(source) || source.level() != hound.level())
            || hound.isSurfaceStalkRetreating() || hound.isFleeing || hound.currentRoll == Roll.FLEE
            || !Double.isFinite(radius) || radius <= 0 || !Float.isFinite(strength) || strength <= 0) return false;
        if (hound.getTarget() != null && !(hound.getTarget() instanceof Player)) return false;
        double distance = position.distanceTo(hound.position());
        double effectiveRadius = hearingRange(radius);
        if (distance > effectiveRadius) return false;
        boolean muffled = hound.level().clip(new ClipContext(hound.getEyePosition(), position.add(0, 0.5, 0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hound)).getType() != HitResult.Type.MISS;
        if (muffled) { effectiveRadius *= 0.5; strength *= 0.6F; }
        if (distance > effectiveRadius || activity == Activity.RECOVERING && strength < 20) return false;
        hound.cancelItemInspection();
        if (hound.tickCount - heardAt < 4 && strength <= lastStrength) return false;
        heardAt = hound.tickCount; heardPlayer = source == null ? null : source.getUUID(); lastStrength = strength;
        lastHeard = Vec3.atBottomCenterOf(BlockPos.containing(position));
        boolean repeated = noisePlaces.stream().anyMatch(place -> place.distSqr(BlockPos.containing(position)) <= 16);
        noisePlaces.addLast(BlockPos.containing(position));
        while (noisePlaces.size() > 4) noisePlaces.removeFirst();
        suspicion = Math.min(100, suspicion + strength * (repeated ? 1.3F : 1.0F));
        Player focus = hound.getSurfaceStalkTarget();
        if (focus == null && hound.getTarget() instanceof Player player) focus = player;
        if (focus != null && canSee(focus, true)) {
            observe(focus);
            if (!hound.isAggressive() && suspicion >= 75) hound.beginPlayerPursuit(focus);
            return true;
        }
        // Sound gives a bounded area to inspect, not a lock on the source entity.
        Vec3 estimate = lastHeard;
        if (distance > 12) estimate = estimate.add((hound.getRandom().nextDouble() - 0.5) * 4, 0, (hound.getRandom().nextDouble() - 0.5) * 4);
        boolean alreadySearching = activity == Activity.SEARCHING;
        boolean fresh = !controlsMovement();
        if (fresh) beganAt = hound.tickCount;
        activity = alreadySearching || repeated && suspicion >= 35 ? Activity.SEARCHING : Activity.INVESTIGATING;
        evidence = estimate; evidenceAt = hound.tickCount;
        int budget = ServerConfig.HUNT_SEARCH_SECONDS.get() * 20;
        deadline = fresh ? hound.tickCount + budget : Math.min(beganAt + budget * 2, Math.max(deadline, hound.tickCount + budget));
        atEvidence = false; revision++; legs = 0; searched.clear();
        hound.beginEvidenceInvestigation();
        return true;
    }

    public void acquired(Player player) {
        hound.cancelItemInspection();
        activity = Activity.NONE; angry = false; atEvidence = false; searched.clear();
        observe(player);
    }

    public void tick() {
        if (!enabled()) {
            if (activity != Activity.NONE) { activity = Activity.NONE; hound.beginEvidenceInvestigation(); }
            return;
        }
        if (hound.isAvoidingHazard()) return;
        if (hound.isSurfaceStalkRetreating() || hound.isFleeing || hound.currentRoll == Roll.FLEE) {
            if (controlsMovement()) { activity = Activity.NONE; hound.getNavigation().stop(); }
            return;
        }
        Player focus = hound.getSurfaceStalkTarget();
        if (focus == null && hound.getTarget() instanceof Player player) focus = player;
        if (focus != null) {
            if (canSee(focus, true)) { observe(focus); return; }
            if (lastSeen != null && hound.tickCount - seenAt <= ServerConfig.HUNT_MEMORY_SECONDS.get() * 20) {
                angry = hound.isAggressive() || hound.currentRoll == Roll.CHASE;
                activity = Activity.SEARCHING; evidence = lastSeen; evidenceAt = seenAt; beganAt = hound.tickCount;
                deadline = hound.tickCount + ServerConfig.HUNT_SEARCH_SECONDS.get() * 20;
                atEvidence = false; revision++; legs = 0; searched.clear();
                hound.beginEvidenceInvestigation();
            } else recover();
            return;
        }
        if (hound.getTarget() != null) return;
        if (hound.tickCount % 20 == 0 && hound.tickCount - heardAt > 20) suspicion = Math.max(0, suspicion - 1);
        if (activity == Activity.RECOVERING) {
            if (hound.tickCount >= recoveryUntil) { activity = Activity.NONE; return; }
            if (hound.tickCount % 5 == 0) {
                Player close = visiblePlayer(12);
                if (close != null) { acquired(close); hound.beginPlayerPursuit(close); }
            }
            return;
        }
        if (activity == Activity.NONE && hound.tickCount > 0 && hound.tickCount % 10 == 0) {
            Player noticed = visiblePlayer(Math.max(ServerConfig.HUNT_VISION_RANGE.get(), ServerConfig.HUNT_LIT_VISION_RANGE.get()));
            if (noticed != null) {
                if (hound.distanceToSqr(noticed) <= 12 * 12) hound.beginPlayerPursuit(noticed);
                else if (!hound.tryStartSurfaceStalking(noticed)) { hound.currentRoll = Roll.STARE; hound.setTarget(noticed); }
            }
            return;
        }
        if (!controlsMovement()) return;
        if (hound.tickCount >= deadline || hound.tickCount - evidenceAt > ServerConfig.HUNT_MEMORY_SECONDS.get() * 20) { recover(); return; }
        if (hound.tickCount % 5 != 0) return;
        Player found = visiblePlayer(Math.max(ServerConfig.HUNT_VISION_RANGE.get(), ServerConfig.HUNT_LIT_VISION_RANGE.get()));
        if (found == null) return;
        boolean pursue = angry || suspicion >= 60 || hound.distanceToSqr(found) <= 12 * 12;
        acquired(found);
        if (pursue) hound.beginPlayerPursuit(found);
        else if (!hound.tryStartSurfaceStalking(found)) { hound.currentRoll = Roll.STARE; hound.setTarget(found); }
    }

    private Player visiblePlayer(double range) {
        return hound.level().players().stream().filter(player -> hound.distanceToSqr(player) <= range * range && canSee(player, false))
            .min(java.util.Comparator.comparingDouble(hound::distanceToSqr)).orElse(null);
    }
    public void reached(Vec3 position) {
        atEvidence = true; legs++;
        searched.addLast(BlockPos.containing(position));
        while (searched.size() > 6) searched.removeFirst();
        if (activity == Activity.INVESTIGATING) activity = Activity.SEARCHING;
    }
    public boolean alreadySearched(Vec3 point) {
        return searched.stream().anyMatch(position -> position.distSqr(BlockPos.containing(point)) < 9);
    }
    public void disengage() { recover(); }
    private void recover() {
        activity = Activity.RECOVERING;
        recoveryUntil = hound.tickCount + ServerConfig.HUNT_RECOVERY_SECONDS.get() * 20;
        suspicion = Math.min(suspicion, 35); angry = false; searched.clear(); noisePlaces.clear();
        hound.beginEvidenceInvestigation();
    }
}
