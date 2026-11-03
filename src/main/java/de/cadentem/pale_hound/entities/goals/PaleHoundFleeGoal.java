package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class PaleHoundFleeGoal extends Goal {
   private final PaleHoundEntity paleHound;
   private final double speedModifier;
   private float ticksUntilLeave;
   private float ticksUntilFlee;
   private boolean shouldLeave;
   private Path fleePath;
   private int ticksUntilNextPathRecalculation;

   public PaleHoundFleeGoal(PaleHoundEntity paleHound, float ticksUntilLeave, double speedModifier) {
      this.paleHound = paleHound;
      this.ticksUntilLeave = ticksUntilLeave;
      this.ticksUntilFlee = 10.0F;
      this.speedModifier = speedModifier;
   }

   public boolean canUse() {
      if (this.paleHound.isInvisible()) {
         return false;
      } else {
         return this.paleHound.currentRoll != Roll.FLEE ? false : this.paleHound.getTarget() != null;
      }
   }

   public boolean canContinueToUse() {
      return this.paleHound.currentRoll != Roll.FLEE ? false : this.paleHound.getTarget() != null;
   }

   public void start() {
      this.setFleePath();
      this.shouldLeave = false;
   }

   public void tick() {
      LivingEntity target = this.paleHound.getTarget();
      if (this.shouldLeave && !this.paleHound.targetIsFacingMe) {
         this.paleHound.disappear();
      }

      this.ticksUntilFlee--;
      this.tickStareClock();
      if (this.ticksUntilFlee <= 0.0F) {
         this.fleeTick();
         this.paleHound.isFleeing = true;
         this.paleHound.getEntityData().set(PaleHoundEntity.FLEEING_ACCESSOR, true);
      } else if (target != null) {
         this.paleHound.getLookControl().setLookAt(target, 180.0F, 1.0F);
      }
   }

   private void setFleePath() {
      LivingEntity target = this.paleHound.getTarget();
      if (target != null) {
         Vec3 fleePosition = DefaultRandomPos.getPosAway(this.paleHound, 32, 7, target.position());
         if (fleePosition != null) {
            this.fleePath = this.paleHound.getNavigation().createPath(fleePosition.x, fleePosition.y, fleePosition.z, 0);
         }
      }
   }

   public void tickStareClock() {
      this.ticksUntilLeave--;
      if (this.ticksUntilLeave < 0.0F) {
         this.shouldLeave = true;
      }
   }

   public void fleeTick() {
      if (this.fleePath == null || this.fleePath.isDone()) {
         this.setFleePath();
      }

      this.paleHound.playFleeSound();
      this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
      if (this.ticksUntilNextPathRecalculation == 0) {
         this.ticksUntilNextPathRecalculation = 2;
         if (!this.paleHound.getNavigation().moveTo(this.fleePath, this.speedModifier)) {
            this.ticksUntilNextPathRecalculation += 2;
         }

         this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
      }
   }
}
