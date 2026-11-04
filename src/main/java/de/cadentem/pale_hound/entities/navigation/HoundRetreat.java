package de.cadentem.pale_hound.entities.navigation;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import java.util.List;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Keep a stable escape heading and reject routes that loop back toward the threat. */
public final class HoundRetreat {
    private Vec3 direction;

    public void reset() { direction = null; }

    @Nullable public Path findPath(PaleHoundEntity hound, Vec3 threat) {
        return findPath(hound, threat, false);
    }

    @Nullable public Path findPath(PaleHoundEntity hound, Vec3 threat, boolean careful) {
        Vec3 away = hound.position().subtract(threat).multiply(1, 0, 1).normalize();
        if (away.lengthSqr() < 0.001) away = new Vec3(1, 0, 0);
        if (direction == null || direction.dot(away) < 0.25) direction = away;
        double startDistance = horizontalDistance(hound.position(), threat);
        for (Vec3 offset : List.of(direction.scale(24), direction.yRot((float)(Math.PI / 6)).scale(24),
            direction.yRot((float)(-Math.PI / 6)).scale(24), direction.scale(12))) {
            Path path = HoundRoutes.findReachable(hound, List.of(hound.position().add(offset)));
            if (path == null) continue;
            Vec3 end = Vec3.atBottomCenterOf(path.getTarget());
            if (horizontalDistance(end, threat) < startDistance + 4
                || end.subtract(hound.position()).dot(direction) < 4) continue;
            boolean returnsTowardPlayer = false;
            for (int i = 0; i < path.getNodeCount(); ++i) {
                if (careful && i > 0 && path.getNode(i - 1).y - path.getNode(i).y > 1) {
                    returnsTowardPlayer = true;
                    break;
                }
                if (horizontalDistance(Vec3.atBottomCenterOf(path.getNode(i).asBlockPos()), threat) < Math.max(0, startDistance - 1)) {
                    returnsTowardPlayer = true;
                    break;
                }
            }
            if (!returnsTowardPlayer) return path;
        }
        return null;
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }
}
