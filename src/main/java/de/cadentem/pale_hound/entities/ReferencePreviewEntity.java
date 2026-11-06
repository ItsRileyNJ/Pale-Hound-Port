package de.cadentem.pale_hound.entities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Separate, stationary art maquette. Never participates in Pale Hound encounter AI. */
public final class ReferencePreviewEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public ReferencePreviewEntity(EntityType<? extends ReferencePreviewEntity> type, Level level) {
        super(type, level); setNoAi(true); setPersistenceRequired();
    }
    @Override protected void registerGoals() {}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
}
