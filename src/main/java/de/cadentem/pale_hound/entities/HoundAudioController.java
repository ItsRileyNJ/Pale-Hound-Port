package de.cadentem.pale_hound.entities;

import de.cadentem.pale_hound.entities.goals.Roll;
import de.cadentem.pale_hound.network.HoundSoundCue;
import de.cadentem.pale_hound.network.HoundSoundCue.Kind;
import de.cadentem.pale_hound.util.Utils;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** Audio follows existing encounters; it does not select targets or change combat behavior. */
public class HoundAudioController {
    private final PaleHoundEntity hound;
    private UUID lastNoticed;
    private int nextNoticeTick;
    private int nextStalkTick;
    private Player stalkingListener;

    public HoundAudioController(PaleHoundEntity hound) { this.hound = hound; }

    public void playerDetected(Player player) {
        if (hound.level().isClientSide() || hound.isSilent() || !Utils.isValidTarget(player)) return;
        if (player.getUUID().equals(lastNoticed) && hound.tickCount < nextNoticeTick) return;
        stopStalking();
        lastNoticed = player.getUUID();
        nextNoticeTick = hound.tickCount + 900;
        nextStalkTick = hound.tickCount + 360 + hound.getRandom().nextInt(101);
        emit(player, Utils.isOnSurface(hound) ? Kind.SEES_PLAYER_SURFACE : Kind.SEES_PLAYER);
    }

    public boolean isUndergroundStalking() {
        return hound.isAlive() && !hound.isSilent() && !hound.isInvisible() && !hound.isAggressive() && !hound.isFleeing
            && !hound.isSurfaceStalking() && !Utils.isOnSurface(hound)
            && (hound.currentRoll == Roll.STARE || hound.currentRoll == Roll.STROLL)
            && hound.getTarget() instanceof Player player && Utils.isValidTarget(player);
    }

    public void tick() {
        if (!isUndergroundStalking()) { stopStalking(); return; }
        Player player = (Player)hound.getTarget();
        if (stalkingListener != null && stalkingListener != player) stopStalking();
        if (hound.tickCount < nextStalkTick) return;
        stalkingListener = player;
        emit(player, Kind.STALKING_UNDERGROUND);
        nextStalkTick = hound.tickCount + 400 + hound.getRandom().nextInt(301);
    }

    public void stopStalking() {
        if (stalkingListener != null) emit(stalkingListener, Kind.STOP_STALKING);
        stalkingListener = null;
    }

    protected void emit(Player player, Kind kind) {
        if (player instanceof ServerPlayer serverPlayer)
            PacketDistributor.sendToPlayer(serverPlayer, new HoundSoundCue(kind, hound.getUUID()));
    }
}
