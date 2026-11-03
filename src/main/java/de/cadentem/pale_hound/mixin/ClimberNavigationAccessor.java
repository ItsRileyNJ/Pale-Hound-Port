package de.cadentem.pale_hound.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WallClimberNavigation.class)
public interface ClimberNavigationAccessor {
    @Accessor("pathToPosition") BlockPos pale_hound$getClimbTarget();
    @Accessor("pathToPosition") void pale_hound$setClimbTarget(BlockPos position);
}
