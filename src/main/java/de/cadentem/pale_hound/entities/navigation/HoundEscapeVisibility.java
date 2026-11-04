package de.cadentem.pale_hound.entities.navigation;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Leaving requires a sustained loss of visibility, rather than merely looking away. */
public final class HoundEscapeVisibility {
    private int elapsed, unseen;
    public void reset() { elapsed = 0; unseen = 0; }
    public boolean canDisappear(PaleHoundEntity hound, int minimumTicks) {
        elapsed++;
        unseen = isVisible(hound) ? 0 : unseen + 1;
        return elapsed >= minimumTicks && unseen >= 20;
    }
    public static boolean isVisible(PaleHoundEntity hound) {
        // Beyond the entity's server tracking radius no client receives its model.
        double range = hound.getType().clientTrackingRange() * 16.0;
        if (hound.level().getServer() != null) range = hound.level().getServer().getScaledTrackingDistance((int)range);
        double visualHeight = hound.isCrawling() ? switch (hound.getCrawlPose()) {
            case TALL -> 2.15; case TUNNEL -> 1.35; case LOW -> 0.9;
        } : hound.getBbHeight() * 0.9;
        for (var player : hound.level().players()) {
            double playerRange = range;
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                int view = Math.max(2, Math.min(serverPlayer.requestedViewDistance(), serverPlayer.getServer().getPlayerList().getViewDistance()));
                playerRange = Math.min(playerRange, view * 16.0);
            }
            playerRange += 16; // Let the body travel beyond the last tracked position before removing it.
            if (!player.isAlive() || player.isRemoved()
                || Math.hypot(player.getX() - hound.getX(), player.getZ() - hound.getZ()) > playerRange) continue;
            if (player.hasLineOfSight(hound)) return true;
            // The physical crawl eye is low; do not disappear while the tall head is still exposed.
            var top = hound.position().add(0, visualHeight, 0);
            if (hound.level().clip(new ClipContext(player.getEyePosition(), top,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }
}
