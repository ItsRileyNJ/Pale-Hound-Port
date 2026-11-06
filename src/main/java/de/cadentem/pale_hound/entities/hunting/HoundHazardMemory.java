package de.cadentem.pale_hound.entities.hunting;

import java.util.EnumMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Bounded experience persists in the save, never in a global JVM/static map. */
public final class HoundHazardMemory extends SavedData {
    private final EnumMap<HoundHazardLearning.Hazard, Integer> experience = new EnumMap<>(HoundHazardLearning.Hazard.class);
    private static final Factory<HoundHazardMemory> FACTORY = new Factory<>(HoundHazardMemory::new, (tag, provider) -> load(tag));
    public static HoundHazardMemory get(ServerLevel level) {
        var overworld = level.getServer().overworld();
        return (overworld == null ? level : overworld).getDataStorage().computeIfAbsent(FACTORY, "pale_hound_hazard_memory");
    }
    public int score(HoundHazardLearning.Hazard hazard) { return experience.getOrDefault(hazard, 0); }
    public void remember(HoundHazardLearning.Hazard hazard, int score) {
        int next = Math.max(score(hazard), Math.min(3, Math.max(0, score)));
        if (next != score(hazard)) { experience.put(hazard, next); setDirty(); }
    }
    public static HoundHazardMemory load(CompoundTag tag) {
        var memory = new HoundHazardMemory();
        for (var hazard : HoundHazardLearning.Hazard.values()) memory.experience.put(hazard, Math.clamp(tag.getInt(hazard.name()), 0, 3));
        return memory;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        for (var hazard : HoundHazardLearning.Hazard.values()) tag.putInt(hazard.name(), score(hazard));
        return tag;
    }
}
