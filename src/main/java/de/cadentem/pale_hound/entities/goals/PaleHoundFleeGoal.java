package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

public class PaleHoundFleeGoal extends Goal {
   private final PaleHoundEntity paleHound;
   private final double speedModifier;
   private final float leaveDuration;
   private float ticksUntilFlee;
   private Path fleePath;
   private int ticksUntilNextPathRecalculation;
   private final de.cadentem.pale_hound.entities.navigation.HoundEscapeVisibility visibility = new de.cadentem.pale_hound.entities.navigation.HoundEscapeVisibility();
   private final de.cadentem.pale_hound.entities.navigation.HoundRetreat escape = new de.cadentem.pale_hound.entities.navigation.HoundRetreat();

   public PaleHoundFleeGoal(PaleHoundEntity paleHound, float ticksUntilLeave, double speedModifier) {
      this.paleHound = paleHound;
      this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
      this.leaveDuration = ticksUntilLeave;
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
      this.ticksUntilFlee = 10;
      this.ticksUntilNextPathRecalculation = 0;
      this.escape.reset();
      this.visibility.reset();
      this.setFleePath();
   }

   @Override public void stop() {
      this.paleHound.getNavigation().stop();
      this.paleHound.setBackingAway(false);
      this.paleHound.isFleeing = false;
      this.paleHound.getEntityData().set(PaleHoundEntity.FLEEING_ACCESSOR, false);
   }

   public void tick() {
      LivingEntity target = this.paleHound.getTarget();
      if (this.visibility.canDisappear(this.paleHound, (int)this.leaveDuration + 10)) {
         this.paleHound.disappear();
         return;
      }

      this.ticksUntilFlee--;
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
         this.fleePath = this.escape.findPath(this.paleHound, target.position());
      }
   }

   public void fleeTick() {
      this.paleHound.playFleeSound();
      this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
      if (this.paleHound.getNavigation().isDone() && this.ticksUntilNextPathRecalculation == 0) {
         this.setFleePath();
         if (this.fleePath != null) this.paleHound.getNavigation().moveTo(this.fleePath, this.speedModifier);
         this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(10);
      }
   }
}
