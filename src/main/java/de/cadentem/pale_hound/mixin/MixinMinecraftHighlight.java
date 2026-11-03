package de.cadentem.pale_hound.mixin;

import de.cadentem.pale_hound.client.HoundHighlights;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftHighlight {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void pale_hound$highlightForViewer(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof PaleHoundEntity && HoundHighlights.isHighlighted(entity)) callback.setReturnValue(true);
    }
}
