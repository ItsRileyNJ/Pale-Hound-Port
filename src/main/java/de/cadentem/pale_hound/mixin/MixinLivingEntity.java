package de.cadentem.pale_hound.mixin;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public abstract class MixinLivingEntity {
   @Inject(
      method = {"travel"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void pleaseStopMoving(Vec3 travelVector, CallbackInfo callback) {
      if ((Object)this instanceof PaleHoundEntity paleHound && paleHound.pleaseStopMoving) {
         callback.cancel();
      }
   }
}
