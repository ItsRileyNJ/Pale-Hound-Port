package de.cadentem.pale_hound.entities.navigation;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/** Follow an outward path using reverse/sideways steps while watching the stalking target. */
public final class HoundMoveControl extends MoveControl {
    private final PaleHoundEntity hound;
    public HoundMoveControl(PaleHoundEntity hound) { super(hound); this.hound = hound; }

    @Override public void tick() {
        boolean moving = operation == Operation.MOVE_TO || operation == Operation.JUMPING;
        float previousYaw = hound.getYRot();
        // Clear lateral input before returning to ordinary forward navigation.
        hound.setXxa(0);
        super.tick();
        var player = hound.getSurfaceStalkTarget();
        if (!hound.isBackingAway() || player == null) return;
        Vec3 toward = player.position().subtract(hound.position());
        float facing = (float)Math.toDegrees(Math.atan2(-toward.x, toward.z));
        hound.setYRot(rotlerp(previousYaw, facing, 20));
        hound.setYBodyRot(hound.getYRot());
        hound.getLookControl().setLookAt(player, 30, 30);
        if (!moving) { hound.setZza(0); return; }
        double dx = wantedX - hound.getX(), dz = wantedZ - hound.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 0.01) { hound.setZza(0); return; }
        double yaw = Math.toRadians(hound.getYRot());
        float forward = (float)((dz * Math.cos(yaw) - dx * Math.sin(yaw)) / length);
        float sideways = (float)((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / length);
        // Do not walk forward into the player if a route or sudden turn asks for it.
        if (forward > 0.05F) { hound.setZza(0); hound.setXxa(0); return; }
        hound.setZza(forward * hound.getSpeed());
        hound.setXxa(sideways * hound.getSpeed());
    }
}
