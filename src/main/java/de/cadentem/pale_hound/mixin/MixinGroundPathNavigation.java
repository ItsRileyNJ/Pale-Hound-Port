package de.cadentem.pale_hound.mixin;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({GroundPathNavigation.class})
public abstract class MixinGroundPathNavigation extends PathNavigation {
   public MixinGroundPathNavigation(Mob mob, Level level) {
      super(mob, level);
   }

   @Inject(
      method = {"canUpdatePath"},
      at = {@At("RETURN")},
      cancellable = true
   )
   public void canUpdateWhenClimbing(CallbackInfoReturnable<Boolean> cir) {
      if (this.mob instanceof PaleHoundEntity paleHound && !(Boolean)cir.getReturnValue() && (!paleHound.hasSpawned() || paleHound.isCrawling())) {
         cir.setReturnValue(true);
      }
   }
}
