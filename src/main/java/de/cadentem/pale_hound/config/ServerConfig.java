package de.cadentem.pale_hound.config;

import de.cadentem.pale_hound.datagen.ModBiomeTagsProvider;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ServerConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    public static ModConfigSpec.DoubleValue SPAWN_TIMER_MULTIPLIER;
    public static ModConfigSpec.DoubleValue PRESENCE_TIME_MULTIPLIER;
    public static ModConfigSpec.IntValue ROAM_RADIUS;
    public static ModConfigSpec.IntValue ROAM_INTERVAL;
    public static ModConfigSpec.IntValue CAN_SPAWN_MIN;
    public static ModConfigSpec.IntValue CAN_SPAWN_MAX;
    public static ModConfigSpec.IntValue CAN_SPAWN_COOLDOWN;
    public static ModConfigSpec.DoubleValue CAN_SPAWN_COOLDOWN_CHANCE;
    public static ModConfigSpec.IntValue RESET_NOISE_MIN;
    public static ModConfigSpec.IntValue RESET_NOISE_MAX;
    public static ModConfigSpec.IntValue TIME_UNTIL_LEAVE;
    public static ModConfigSpec.IntValue TIME_UNTIL_LEAVE_CHASE;
    public static ModConfigSpec.DoubleValue SURFACE_TIMER_MULTIPLIER;
    public static ModConfigSpec.DoubleValue SPAWN_CHANCE_PER_TICK;
    public static ModConfigSpec.DoubleValue FOREST_SPAWN_CHANCE_MULTIPLIER;
    public static ModConfigSpec.ConfigValue<Integer> SPAWN_HEIGHT;
    public static ModConfigSpec.BooleanValue ALLOW_SURFACE_SPAWN;
    public static ModConfigSpec.IntValue SKY_LIGHT_LEVEL;
    public static ModConfigSpec.IntValue BLOCK_LIGHT_LEVEL;
    public static ModConfigSpec.IntValue MAXIMUM_AMOUNT;
    public static ModConfigSpec.IntValue SPAWN_DISTANCE;
    public static ModConfigSpec.BooleanValue CHECK_PATH_TO_SPAWN;
    public static ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_WHITELIST;
    public static ModConfigSpec.BooleanValue OVERRIDE_BIOME_DATAPACK_CONFIG;
    public static ModConfigSpec.BooleanValue SURFACE_BIOMES_IS_WHITELIST;
    public static ModConfigSpec.ConfigValue<List<? extends String>> SURFACE_BIOMES;
    public static ModConfigSpec.IntValue SPOTTING_RANGE;
    public static ModConfigSpec.BooleanValue CAN_CLIMB;
    public static ModConfigSpec.BooleanValue ALLOW_RIDING;
    public static ModConfigSpec.BooleanValue TARGET_INVISIBLE;
    public static ModConfigSpec.DoubleValue ARMOR;
    public static ModConfigSpec.DoubleValue MAX_HEALTH;
    public static ModConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static ModConfigSpec.DoubleValue ATTACK_SPEED;
    public static ModConfigSpec.DoubleValue MOVEMENT_SPEED;
    public static ModConfigSpec.DoubleValue DEPTH_STRIDER_BONUS;
    public static ModConfigSpec.BooleanValue ONLY_PLAY_NOISE_TO_TARGET;

    public static final List<String> DEFAULT_SURFACE_BIOME_BLACKLIST = List.of("minecraft:badlands", "minecraft:wooded_badlands", "minecraft:eroded_badlands", "minecraft:desert");
    public static final List<String> LEGACY_SURFACE_BIOME_BLACKLIST = List.of("minecraft:badlands", "minecraft:bamboo_jungle", "minecraft:basalt_deltas", "minecraft:beach", "minecraft:birch_forest", "minecraft:cold_ocean", "minecraft:crimson_forest", "minecraft:dark_forest", "minecraft:deep_cold_ocean", "minecraft:deep_frozen_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:deep_ocean", "minecraft:desert", "minecraft:eroded_badlands", "minecraft:flower_forest", "minecraft:forest", "minecraft:grove", "minecraft:jungle", "minecraft:lukewarm_ocean", "minecraft:mangrove_swamp", "minecraft:meadow", "minecraft:mushroom_fields", "minecraft:ocean", "minecraft:old_growth_birch_forest", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:plains", "minecraft:river", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:sparse_jungle", "minecraft:stony_peaks", "minecraft:stony_shore", "minecraft:sunflower_plains", "minecraft:swamp", "minecraft:taiga", "minecraft:warm_ocean", "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills", "minecraft:wooded_woodlands");

    public static boolean migrateDefaultBiomeBlacklist() {
        if (OVERRIDE_BIOME_DATAPACK_CONFIG.get() && !SURFACE_BIOMES_IS_WHITELIST.get()
            && new java.util.HashSet<>(SURFACE_BIOMES.get()).equals(new java.util.HashSet<>(LEGACY_SURFACE_BIOME_BLACKLIST))) {
            SURFACE_BIOMES.set(DEFAULT_SURFACE_BIOME_BLACKLIST);
            return true;
        }
        return false;
    }

    private static boolean resourcePredicate(Object element) {
        if (element == null) {
            return false;
        }
        if (element instanceof String) {
            String string = (String)element;
            return string.split(":").length == 2;
        }
        if (element instanceof List) {
            List list = (List)element;
            for (Object listElement : list) {
                if (listElement instanceof String) {
                    String string = (String)listElement;
                    if (string.split(":").length == 2) continue;
                    return false;
                }
                return false;
            }
            return true;
        }
        return false;
    }

    public static boolean isValidDimension(String key) {
        return ((List)DIMENSION_WHITELIST.get()).contains(key);
    }

    public static boolean isInValidBiome(Entity entity) {
        if (entity == null) {
            return false;
        }
        Level level = entity.level();
        if (level instanceof ServerLevel) {
            ServerLevel serverLevel = (ServerLevel)level;
            Holder biome = serverLevel.getBiome(entity.blockPosition());
            if (!de.cadentem.pale_hound.util.PaleHoundBiomes.isAllowed(biome)) return false;
            boolean isWhitelist = (Boolean)SURFACE_BIOMES_IS_WHITELIST.get();
            boolean isBiomeInList = false;
            if (((Boolean)OVERRIDE_BIOME_DATAPACK_CONFIG.get()).booleanValue()) {
                Optional unwrapped = biome.unwrapKey();
                if (unwrapped.isPresent()) {
                    isBiomeInList = ((List)SURFACE_BIOMES.get()).contains(((ResourceKey)unwrapped.get()).location().toString());
                }
            } else {
                isBiomeInList = biome.is(ModBiomeTagsProvider.PALE_HOUND_SURFACE_BIOMES);
            }
            return isWhitelist && isBiomeInList || !isWhitelist && !isBiomeInList;
        }
        return false;
    }

    static {
        BUILDER.push("Timers");
        BUILDER.push("Spawn");
        SPAWN_TIMER_MULTIPLIER = BUILDER.comment("Scale spawn waits and cooldowns; 0.8 means 20% shorter waits, 1.0 restores original timing. Does not affect noise timers.").defineInRange("spawn_timer_multiplier", 0.8, 0.1, 5.0);
        CAN_SPAWN_MIN = BUILDER.comment("Minimum time between spawns in seconds").defineInRange("can_spawn_min", 800, 0, 86400);
        CAN_SPAWN_MAX = BUILDER.comment("Maximum time between spawns in seconds").defineInRange("can_spawn_max", 1200, 0, 86400);
        CAN_SPAWN_COOLDOWN_CHANCE = BUILDER.comment("Chance for a spawn cooldown to occur").defineInRange("can_spawn_cooldown_chance", 0.3, 0.0, 1.0);
        CAN_SPAWN_COOLDOWN = BUILDER.comment("Spawn cooldown length in seconds").defineInRange("can_spawn_cooldown", 1400, 0, 86400);
        BUILDER.pop();
        BUILDER.push("Noise");
        RESET_NOISE_MIN = BUILDER.comment("Minimum time between noise occurrences in seconds").defineInRange("reset_noise_min", 740, 0, 86400);
        RESET_NOISE_MAX = BUILDER.comment("Maximum time between noise occurrences in seconds").defineInRange("reset_noise_max", 860, 0, 86400);
        BUILDER.pop();
        BUILDER.push("Leave");
        PRESENCE_TIME_MULTIPLIER = BUILDER.comment("Scale normal lifetime, chase duration and stalking duration; forced retreats remain three seconds.").defineInRange("presence_time_multiplier", 3.0, 0.1, 10.0);
        TIME_UNTIL_LEAVE = BUILDER.comment("Time (in seconds) it takes for the Pale Hound to leave").defineInRange("time_until_leave", 300, 1, 6000);
        TIME_UNTIL_LEAVE_CHASE = BUILDER.comment("Time (in seconds) it takes for the Pale Hound to leave once a chase begins").defineInRange("time_until_leave_chase", 30, 1, 600);
        BUILDER.pop();
        SURFACE_TIMER_MULTIPLIER = BUILDER.comment("Modify the timers for the surface (to reduce or increase them), based on the general timer set (e.g. spawn timer of 300 seconds -> 0.3 turns it into 90 seconds and 1.7 turns it into 510 seconds for the surface)").defineInRange("surface_timer_multiplier", 1.0, 0.0, 5.0);
        BUILDER.pop();
        BUILDER.push("Spawn Conditions");
        FOREST_SPAWN_CHANCE_MULTIPLIER = BUILDER.comment("Scale the ready-to-spawn chance in forested biomes; 1.25 is a 25% bonus. Does not change timers.").defineInRange("forest_spawn_chance_multiplier", 1.25, 0.0, 5.0);
        SPAWN_CHANCE_PER_TICK = BUILDER.comment("The spawn chance per tick (once the spawn timer is finished)").defineInRange("spawn_chance_per_tick", 0.005, 0.0, 1.0);
        SPAWN_HEIGHT = BUILDER.comment("Depth at which the Pale Hound can start to spawn").define("spawn_height", 500);
        ALLOW_SURFACE_SPAWN = BUILDER.comment("Whether the Pale Hound can spawn on the surface or not").define("allow_surface_spawn", true);
        SKY_LIGHT_LEVEL = BUILDER.comment("The maximum sky light level the Pale Hound can spawn at").defineInRange("sky_light_level", 8, 0, 15);
        BLOCK_LIGHT_LEVEL = BUILDER.comment("The maximum block light level the Pale Hound can spawn at").defineInRange("block_light_level", 15, 0, 15);
        MAXIMUM_AMOUNT = BUILDER.comment("The maximum amount of pale hounds which can exist at the same time").defineInRange("maximum_amount", 3, 0, 100);
        SPAWN_DISTANCE = BUILDER.comment("How close to players the pale hounds is allowed to spawn (in blocks)").defineInRange("spawn_distance", 20, 0, 64);
        CHECK_PATH_TO_SPAWN = BUILDER.comment("If set to true the pale hounds will try to find a spawn position with a possible path to the player").define("check_path_to_spawn", true);
        BUILDER.push("Dimensions");
        DIMENSION_WHITELIST = BUILDER.comment("The dimensions where the Pale Hound can spawn in (Whitelist)").defineList("dimension_whitelist", List.of("minecraft:overworld"), ServerConfig::resourcePredicate);
        BUILDER.pop();
        BUILDER.push("Biomes");
        OVERRIDE_BIOME_DATAPACK_CONFIG = BUILDER.comment("If you don't want to create a datapack to configure the biomes").define("override_biome_datapack_config", true);
        SURFACE_BIOMES_IS_WHITELIST = BUILDER.comment("Use the surface biome list either as white- or blacklist").define("surface_biomes_is_whitelist", false);
        SURFACE_BIOMES = BUILDER.comment("Either white- or blacklist of the surface biomes the Pale Hound can spawn in (Syntax: modid:biome, e.g. `minecraft:plains`)").defineList("surface_biomes", DEFAULT_SURFACE_BIOME_BLACKLIST, ServerConfig::resourcePredicate);
        BUILDER.pop();
        BUILDER.pop();
        BUILDER.push("Behaviour");
        ROAM_RADIUS = BUILDER.comment("Horizontal radius for idle roaming destination searches.").defineInRange("roam_radius", 24, 8, 64);
        ROAM_INTERVAL = BUILDER.comment("Ticks between idle roaming attempts on average.").defineInRange("roam_interval", 40, 10, 1200);
        SPOTTING_RANGE = BUILDER.comment("The distance in blocks at which the Pale Hound can detect whether a player is looking at it or not").defineInRange("spotting_range", 50, 0, 128);
        CAN_CLIMB = BUILDER.comment("Whether the pale hound can climb or not").define("can_climb", true);
        ALLOW_RIDING = BUILDER.comment("Allow the Pale Hound to follow vanilla riding logic (e.g. boats)").define("allow_riding", false);
        TARGET_INVISIBLE = BUILDER.comment("Whether invisible players can be targets or not").define("target_invisible", true);
        BUILDER.pop();
        BUILDER.push("Attributes");
        ARMOR = BUILDER.comment("Maximum Armor").defineInRange("maximum_armor", 10.0, 1.0, 20.0);
        MAX_HEALTH = BUILDER.comment("Maximum health").defineInRange("maximum_health", 80.0, 1.0, 100000.0);
        ATTACK_DAMAGE = BUILDER.comment("Attack damage").defineInRange("attack_damage", 20.0, 0.0, 1000.0);
        ATTACK_SPEED = BUILDER.comment("Attack speed").defineInRange("attack_speed", 0.35, 0.0, 10.0);
        MOVEMENT_SPEED = BUILDER.comment("Movement speed").defineInRange("movement_speed", 0.7, 0.0, 5.0);
        DEPTH_STRIDER_BONUS = BUILDER.comment("Depth Strider (movement speed in water) bonus").defineInRange("depth_strider_bonus", 1.5, 0.0, 3.0);
        BUILDER.pop();
        BUILDER.push("Misc");
        ONLY_PLAY_NOISE_TO_TARGET = BUILDER.comment("Only play the ambient noises to the current (spawn) target (Note: The target can change when a spawn attempt is not successful)").define("only_play_noise_to_target", false);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
