package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.util.Utils;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class PaleHoundStareGoal extends Goal {
   private final PaleHoundEntity paleHound;
   private boolean wasNotLookingPreviously;
   private int lookedAtCount;
   private final int lookedAtMax;

   public PaleHoundStareGoal(PaleHoundEntity paleHound) {
      this.paleHound = paleHound;
      this.lookedAtMax = paleHound.getRandom().nextIntBetweenInclusive(4, 13);
   }

   public boolean canUse() {
      if (this.paleHound.isInvisible()) {
         return false;
      } else {
         return !Utils.isValidTarget(this.paleHound.getTarget()) ? false : this.paleHound.currentRoll == Roll.STARE;
      }
   }

   public boolean canContinueToUse() {
      LivingEntity lastHurtByMob = this.paleHound.getLastHurtByMob();
      LivingEntity target = this.paleHound.getTarget();
      if (lastHurtByMob instanceof Player) {
         this.paleHound.currentRoll = Roll.CHASE;
         return super.canContinueToUse();
      } else if (lastHurtByMob instanceof Mob) {
         this.paleHound.currentRoll = Roll.CHASE;
         return super.canContinueToUse();
      } else {
         return !Utils.isValidTarget(target) ? false : this.paleHound.currentRoll == Roll.STARE;
      }
   }

   public boolean requiresUpdateEveryTick() {
      return true;
   }

   public void stop() {
      super.stop();
      this.lookedAtCount = 0;
      this.wasNotLookingPreviously = false;
      this.paleHound.pleaseStopMoving = false;
      this.paleHound.setSpotted(false);
   }

   public void tick() {
      LivingEntity target = this.paleHound.getTarget();
      if (target == null) {
         this.paleHound.disappear();
      } else {
         boolean actuallyLooking = this.paleHound.targetIsFacingMe && target.hasLineOfSight(this.paleHound);
         if (this.wasNotLookingPreviously && actuallyLooking) {
            this.lookedAtCount++;
         }

         if (this.lookedAtCount > this.lookedAtMax) {
            if (!actuallyLooking && this.paleHound.getRandom().nextDouble() < 0.1) {
               this.paleHound.disappear();
            } else if (this.paleHound.getRandom().nextDouble() < 0.3) {
               this.paleHound.pickRoll(List.of(Roll.CHASE, Roll.FLEE));
            }
         }

         if (!actuallyLooking) {
            this.paleHound.pleaseStopMoving = false;
            this.paleHound.getNavigation().moveTo(target, 1.0);
         } else {
            this.paleHound.pleaseStopMoving = true;
            this.paleHound.getNavigation().stop();
            this.paleHound.setDeltaMovement(Vec3.ZERO);
         }

         this.paleHound.getLookControl().setLookAt(target);
         this.wasNotLookingPreviously = !actuallyLooking;
      }
   }
}
