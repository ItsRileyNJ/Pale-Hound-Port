package de.cadentem.pale_hound.mixin;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PathNavigation.class})
public abstract class MixinPathNavigation {
   @Unique
   private boolean pale_hound$wasCrawling;
   @Shadow
   @Final
   protected Mob mob;

   @Inject(
      method = {"createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;"},
      at = {@At("HEAD")}
   )
   public void setCrawling_true(
      Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange, CallbackInfoReturnable<Path> callback
   ) {
      if (this.mob instanceof PaleHoundEntity paleHound) {
         this.pale_hound$wasCrawling = paleHound.isCrawling();
         paleHound.setCrawling(true);
      }
   }

   @Inject(
      method = {"createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;"},
      at = {@At("RETURN")}
   )
   public void setCrawling_false(
      Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange, CallbackInfoReturnable<Path> callback
   ) {
      if (this.mob instanceof PaleHoundEntity paleHound) {
         paleHound.setCrawling(this.pale_hound$wasCrawling);
      }
   }
}
