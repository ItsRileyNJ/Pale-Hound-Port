package de.cadentem.pale_hound.entities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/** Targetable anatomy, independent of the narrow cave-navigation collision box. */
public final class HoundCombatPart extends PartEntity<PaleHoundEntity> {
    public HoundCombatPart(PaleHoundEntity parent) { super(parent); this.noPhysics = true; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean isPickable() { return getParent().isAlive() && !getParent().isRemoved(); }
    @Override public boolean hurt(DamageSource source, float amount) { return getParent().hurt(source, amount); }
    @Override public boolean is(Entity entity) { return entity == this || entity == getParent(); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public ItemStack getPickResult() { return getParent().getPickResult(); }
}
