package de.cadentem.pale_hound.util;

import de.cadentem.pale_hound.config.ServerConfig;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;

/** Shared policy for encounter eligibility and the actual candidate spawn position. */
public final class PaleHoundBiomes {
    public static final TagKey<Biome> BLOCKED = tag("blocked_spawn_biomes");
    public static final TagKey<Biome> FORESTED = tag("forested_spawn_biomes");

    private PaleHoundBiomes() {}

    private static TagKey<Biome> tag(String path) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("pale_hound", path));
    }

    public static boolean isAllowed(Holder<Biome> biome) {
        if (biome.is(BLOCKED) || biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS)
            || biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_BADLANDS)
            || biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_DESERT)) return false;
        // Also recognize untagged modded variants by their biome path.
        String path = biome.unwrapKey().map(key -> key.location().getPath().toLowerCase(Locale.ROOT)).orElse("");
        return !path.contains("badlands") && !path.contains("mesa") && !path.contains("desert");
    }

    public static double spawnChance(Holder<Biome> biome) {
        if (!isAllowed(biome)) return 0;
        double chance = ServerConfig.SPAWN_CHANCE_PER_TICK.get();
        if (biome.is(FORESTED)) chance *= ServerConfig.FOREST_SPAWN_CHANCE_MULTIPLIER.get();
        return Math.min(1.0, chance);
    }

    public static double spawnChance(Entity target) {
        return target == null ? 0 : spawnChance(target.level().getBiome(target.blockPosition()));
    }
}
