package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;

public class PaleHoundBreakBlocksGoal extends Goal {
   private final PaleHoundEntity paleHound;

   public PaleHoundBreakBlocksGoal(PaleHoundEntity paleHound) {
      this.paleHound = paleHound;
   }

   public boolean canUse() {
      return true;
   }

   public void tick() {
      if (this.paleHound.horizontalCollision && EventHooks.canEntityGrief(this.paleHound.level(), this.paleHound)) {
         boolean flag = false;
         AABB aabb = this.paleHound.getBoundingBox().inflate(0.2);

         for (BlockPos blockpos : BlockPos.betweenClosed(
            Mth.floor(aabb.minX),
            Mth.floor(aabb.minY),
            Mth.floor(aabb.minZ),
            Mth.floor(aabb.maxX),
            Mth.floor(aabb.maxY),
            Mth.floor(aabb.maxZ)
         )) {
            BlockState blockstate = this.paleHound.level().getBlockState(blockpos);
            Block block = blockstate.getBlock();
            if (block != Blocks.IRON_DOOR && block != Blocks.IRON_TRAPDOOR) {
               if (block == Blocks.GLASS_PANE
                  || block == Blocks.IRON_BARS
                  || block instanceof FenceBlock
                  || block instanceof FenceGateBlock
                  || block instanceof SlabBlock
                  || block instanceof TrapDoorBlock
                  || block instanceof DoorBlock
                  || block instanceof HalfTransparentBlock
                  || block instanceof StainedGlassBlock
                  || block instanceof StainedGlassPaneBlock
                  || block instanceof TintedGlassBlock) {
                  flag = this.paleHound.level().destroyBlock(blockpos, true, this.paleHound) || flag;
               }

               if (block instanceof SignBlock
                  || block instanceof FenceBlock
                  || block instanceof FenceGateBlock
                  || block instanceof TrapDoorBlock
                  || block instanceof DoorBlock) {
                  this.paleHound.playSound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR);
               }
            }
         }

         if (flag) {
            this.paleHound.onGround();
         }
      }
   }
}
