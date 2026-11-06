package de.cadentem.pale_hound.entities.hunting;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LightLayer;

public final class HoundLightAwareness {
    public static final TagKey<Item> HELD_LIGHTS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("pale_hound", "illuminating_items"));
    private HoundLightAwareness() {}
    public static boolean isIlluminated(Player player) {
        return player.getMainHandItem().is(HELD_LIGHTS) || player.getOffhandItem().is(HELD_LIGHTS)
            || player.level().getBrightness(LightLayer.BLOCK, player.blockPosition()) >= 8
            || player.level().getMaxLocalRawBrightness(player.blockPosition()) >= 12;
    }
}
