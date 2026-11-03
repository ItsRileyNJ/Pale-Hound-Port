package de.cadentem.pale_hound.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PathNavigation.class)
public interface NavigationStateAccessor {
    @Accessor("targetPos") BlockPos pale_hound$getTargetPos();
    @Accessor("targetPos") void pale_hound$setTargetPos(BlockPos position);
    @Accessor("reachRange") int pale_hound$getReachRange();
    @Accessor("reachRange") void pale_hound$setReachRange(int range);
}
