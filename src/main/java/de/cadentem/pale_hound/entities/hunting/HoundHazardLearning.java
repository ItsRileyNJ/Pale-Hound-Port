package de.cadentem.pale_hound.entities.hunting;

import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

public final class HoundHazardLearning {
    public enum Hazard { LAVA, FIRE, EXPLOSIVES, WEBS, SHARP_BLOCKS, COLD }
    private final PaleHoundEntity hound;
    private final EnumMap<Hazard, Integer> local = new EnumMap<>(Hazard.class);
    private HoundHazardMemory shared;
    private int signature = -1, explosiveScan = -1000, threatScan = -1000;
    private List<PrimedTnt> explosives = List.of();
    private Vec3 threat;
    private boolean avoiding;
    public HoundHazardLearning(PaleHoundEntity hound) { this.hound = hound; }
    public boolean enabled() { return ServerConfig.SENSORY_HUNTING.get() && ServerConfig.LEARN_HAZARDS.get(); }
    public boolean isAvoiding() { return enabled() && avoiding; }
    public void setAvoiding(boolean value) { avoiding = value; }
    public int score(Hazard hazard) {
        if (!enabled()) return 0;
        int score = local.getOrDefault(hazard, 0);
        if (ServerConfig.SHARE_HAZARD_LEARNING.get() && hound.level() instanceof ServerLevel level) {
            if (shared == null) shared = HoundHazardMemory.get(level);
            score = Math.max(score, shared.score(hazard));
        }
        return score;
    }
    public void learned(Hazard hazard) {
        if (!enabled()) return;
        int next = Math.min(3, score(hazard) + 1); local.put(hazard, next);
        if (ServerConfig.SHARE_HAZARD_LEARNING.get() && hound.level() instanceof ServerLevel level) {
            if (shared == null) shared = HoundHazardMemory.get(level);
            shared.remember(hazard, next);
        }
        signature = -1; threatScan = -1000; explosiveScan = -1000; applyPathCosts();
    }
    /** Called only after actual health damage, so immunity, shields and canceled damage do not teach fear. */
    public void damaged(DamageSource source, float amount) {
        if (!enabled() || amount <= 0) return;
        if (source.is(DamageTypes.LAVA)) learned(Hazard.LAVA);
        if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.HOT_FLOOR)) learned(Hazard.FIRE);
        if (source.is(DamageTypeTags.IS_EXPLOSION)) learned(Hazard.EXPLOSIVES);
        if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) learned(Hazard.SHARP_BLOCKS);
        if (source.is(DamageTypes.FREEZE)) learned(Hazard.COLD);
        if (hound.level().getBlockStates(hound.getBoundingBox().inflate(0.05)).anyMatch(state -> state.is(Blocks.COBWEB))) learned(Hazard.WEBS);
    }
    public void tick() { if (hound.tickCount % 20 == 0) applyPathCosts(); }
    public void applyPathCosts() {
        int packed = enabled() ? 1 : 0;
        for (var hazard : Hazard.values()) packed = packed * 4 + score(hazard);
        if (packed == signature) return;
        signature = packed;
        if (!enabled()) {
            hound.setPathfindingMalus(PathType.LAVA, -1); hound.setPathfindingMalus(PathType.DAMAGE_FIRE, -1);
            hound.setPathfindingMalus(PathType.DANGER_FIRE, 8); hound.setPathfindingMalus(PathType.POWDER_SNOW, -1);
            hound.setPathfindingMalus(PathType.DAMAGE_OTHER, -1); hound.setPathfindingMalus(PathType.DANGER_OTHER, 8);
            return;
        }
        hound.setPathfindingMalus(PathType.LAVA, score(Hazard.LAVA) * 12);
        hound.setPathfindingMalus(PathType.DAMAGE_FIRE, score(Hazard.FIRE) * 12);
        hound.setPathfindingMalus(PathType.DANGER_FIRE, score(Hazard.FIRE) * 6);
        hound.setPathfindingMalus(PathType.POWDER_SNOW, score(Hazard.COLD) * 12);
        hound.setPathfindingMalus(PathType.DAMAGE_OTHER, score(Hazard.SHARP_BLOCKS) * 12);
        hound.setPathfindingMalus(PathType.DANGER_OTHER, Math.max(score(Hazard.WEBS), score(Hazard.EXPLOSIVES)) * 12 + score(Hazard.SHARP_BLOCKS) * 6);
    }
    public boolean threateningItem(Player player) {
        return java.util.stream.Stream.of(player.getMainHandItem(), player.getOffhandItem()).anyMatch(stack ->
            score(Hazard.LAVA) >= 2 && stack.is(Items.LAVA_BUCKET)
            || score(Hazard.WEBS) >= 2 && stack.is(Items.COBWEB)
            || score(Hazard.EXPLOSIVES) >= 2 && (stack.is(Items.TNT) || stack.is(Items.TNT_MINECART))
            || score(Hazard.FIRE) >= 2 && (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)));
    }
    public boolean avoidBlock(BlockState state) {
        return state.is(Blocks.COBWEB) && score(Hazard.WEBS) > 0
            || state.is(Blocks.LAVA) && score(Hazard.LAVA) > 0
            || (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && score(Hazard.FIRE) > 0
            || (state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)) && score(Hazard.SHARP_BLOCKS) > 0
            || state.is(Blocks.POWDER_SNOW) && score(Hazard.COLD) > 0;
    }
    private List<PrimedTnt> explosives() {
        if (score(Hazard.EXPLOSIVES) == 0) return List.of();
        if (hound.tickCount - explosiveScan >= 10) {
            explosiveScan = hound.tickCount;
            explosives = hound.level().getEntitiesOfClass(PrimedTnt.class, hound.getBoundingBox().inflate(24), entity -> !entity.isRemoved() && (hound.distanceToSqr(entity) <= 8 * 8 || hound.hasLineOfSight(entity)));
        }
        return explosives;
    }
    public boolean dangerousExplosiveAt(BlockPos position) {
        double range = 4 + score(Hazard.EXPLOSIVES) * 2;
        for (var explosive : explosives()) if (explosive.distanceToSqr(Vec3.atCenterOf(position)) < range * range) return true;
        return false;
    }
    public Vec3 nearestThreat() {
        if (!enabled()) return null;
        if (hound.tickCount - threatScan < 10) return threat;
        threatScan = hound.tickCount; threat = null;
        for (var explosive : explosives()) {
            double range = 8 + score(Hazard.EXPLOSIVES) * 2;
            if (hound.distanceToSqr(explosive) <= range * range && (hound.distanceToSqr(explosive) <= 8 * 8 || hound.hasLineOfSight(explosive))) choose(explosive.position());
        }
        for (var player : hound.level().players()) if (hound.distanceToSqr(player) <= 16 * 16 && threateningItem(player) && hound.canSeePlayer(player)) choose(player.position());
        if (java.util.Arrays.stream(Hazard.values()).anyMatch(hazard -> score(hazard) > 0)) {
            for (var pos : BlockPos.betweenClosed(hound.blockPosition().offset(-2, -1, -2), hound.blockPosition().offset(2, 2, 2)))
                if (avoidBlock(hound.level().getBlockState(pos))) choose(Vec3.atCenterOf(pos));
        }
        return threat;
    }
    private void choose(Vec3 point) { if (threat == null || point.distanceToSqr(hound.position()) < threat.distanceToSqr(hound.position())) threat = point; }
    public String describe() {
        var scores = new java.util.LinkedHashMap<String, Integer>();
        for (var hazard : Hazard.values()) scores.put(hazard.name().toLowerCase(java.util.Locale.ROOT), score(hazard));
        return scores.toString();
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        for (var hazard : Hazard.values()) tag.putInt(hazard.name(), local.getOrDefault(hazard, 0));
        return tag;
    }
    public void load(CompoundTag tag) {
        for (var hazard : Hazard.values()) local.put(hazard, Math.clamp(tag.getInt(hazard.name()), 0, 3));
        signature = -1; applyPathCosts();
    }
}
