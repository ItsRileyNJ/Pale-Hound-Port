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
        if (path == null) return null;
        BlockPos destination = path.getTarget();
        recentDestinations.addLast(destination.immutable());
        while (recentDestinations.size() > 8) recentDestinations.removeFirst();
        return Vec3.atBottomCenterOf(destination);
    }

    private double explorationScore(Vec3 point) {
        double score = hound.distanceToSqr(point);
        for (BlockPos recent : recentDestinations)
            if (recent.distToCenterSqr(point) < 64) score -= 1024;
        return score;
    }
}
