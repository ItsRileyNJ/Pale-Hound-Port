package de.cadentem.pale_hound.util;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

/** Optional-mod matching uses registry IDs and caravan membership, never a hard class dependency. */
public final class PaleHoundTargets {
    private static final Set<ResourceLocation> MERCENARIES_AND_CARAVANS = Set.of(
        ResourceLocation.fromNamespaceAndPath("villagerscontract", "defender"),
        ResourceLocation.fromNamespaceAndPath("villagerscontract", "caravan_villager"),
        ResourceLocation.fromNamespaceAndPath("villagerscontract", "caravan_trader"),
        ResourceLocation.fromNamespaceAndPath("villagerscontract", "caravan_mule"));

    private PaleHoundTargets() {}

    public static boolean isAllowed(Entity entity) {
        if (!Utils.isValidTarget(entity)) return false;
        return entity instanceof Player || entity instanceof Sheep || entity instanceof Villager
            || entity instanceof IronGolem || entity instanceof Wolf wolf && wolf.isTame()
            || MERCENARIES_AND_CARAVANS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))
            || entity.getType() == EntityType.HORSE && entity.getPersistentData().hasUUID("FFMCaravan");
    }
}
