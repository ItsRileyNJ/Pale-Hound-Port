package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.entities.navigation.HoundRoutes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Longer excursions, preferring destinations outside the recently visited area. */
public final class PaleHoundRoamGoal extends RandomStrollGoal {
    private final PaleHoundEntity hound;
    private final ArrayDeque<BlockPos> recentDestinations = new ArrayDeque<>();

    public PaleHoundRoamGoal(PaleHoundEntity hound, double speed) {
        super(hound, speed, ServerConfig.ROAM_INTERVAL.get(), false);
        this.hound = hound;
        setInterval(ServerConfig.ROAM_INTERVAL.get());
    }

    @Override public boolean canUse() {
        setInterval(ServerConfig.ROAM_INTERVAL.get());
        return hound.currentRoll == Roll.STROLL && hound.getTarget() == null
            && !hound.isSurfaceStalking() && super.canUse();
    }

    @Override public boolean canContinueToUse() {
        return hound.currentRoll == Roll.STROLL && hound.getTarget() == null
            && !hound.isSurfaceStalking() && super.canContinueToUse();
    }

    @Override @Nullable
    public Vec3 getPosition() {
        var candidates = new ArrayList<Vec3>();
        for (int i = 0; i < 8; ++i) {
            Vec3 point = LandRandomPos.getPos(hound, ServerConfig.ROAM_RADIUS.get(), 8);
            if (point != null && !candidates.contains(point)) candidates.add(point);
        }
        candidates.sort(Comparator.comparingDouble(this::explorationScore).reversed());
        var path = HoundRoutes.findReachable(hound, candidates);
        if (path == null) {
            // Random land samples often miss the floor below a small cave ledge.
            // Project nearby exits down to ground before giving up on roaming.
            double radius = Math.min(8, ServerConfig.ROAM_RADIUS.get());
            path = HoundRoutes.findReachable(hound, java.util.List.of(
                hound.position().add(radius, 0, 0), hound.position().add(-radius, 0, 0),
                hound.position().add(0, 0, radius), hound.position().add(0, 0, -radius)));
        }
        if (path == null) return null;
        BlockPos destination = path.getTarget();
        recentDestinations.addLast(destination.immutable());
        while (recentDestinations.size() > 8) recentDestinations.removeFirst();
        return Vec3.atBottomCenterOf(destination);
    }

    private double explorationScore(Vec3 point) {
        double dx = point.x - hound.getX(), dz = point.z - hound.getZ();
        double rise = Math.max(0, point.y - hound.getY());
        double score = dx * dx + dz * dz - 4 * rise * rise;
        for (BlockPos recent : recentDestinations)
            if (recent.distToCenterSqr(point) < 64) score -= 1024;
        return score;
    }
}
