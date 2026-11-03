package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.util.Utils;
import java.util.EnumSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;

public class PaleHoundChaseGoal extends Goal {
   private final PaleHoundEntity paleHound;
   private final int maxSpeedReached;
   private final boolean followTargetEvenIfNotSeen;
   private LivingEntity previousTarget;
   private long lastGameTimeCheck;
   private int ticksUntilLeave;
   private int ticksUntilNextAttack;
   private int speedUp;

   public PaleHoundChaseGoal(PaleHoundEntity paleHound, boolean followTargetEvenIfNotSeen) {
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
      this.paleHound = paleHound;
      this.followTargetEvenIfNotSeen = followTargetEvenIfNotSeen;
      this.ticksUntilLeave = Utils.presenceTicks((Integer)ServerConfig.TIME_UNTIL_LEAVE_CHASE.get());
      this.maxSpeedReached = Utils.secondsToTicks(3);
   }

   public boolean canUse() {
      if (this.paleHound.isInvisible()) {
         return false;
      } else if (this.paleHound.currentRoll != Roll.CHASE) {
         return false;
      } else if (!this.paleHound.targetIsFacingMe) {
         return false;
      } else {
         long ticks = this.paleHound.level().getGameTime();
         if (ticks - this.lastGameTimeCheck < 20L) {
            return false;
         } else {
            this.lastGameTimeCheck = ticks;
            LivingEntity target = this.paleHound.getTarget();
            if (!Utils.isValidTarget(target)) {
               return false;
            } else {
               Path path = this.paleHound.getNavigation().createPath(target, 0);
               if (path != null) {
                  return true;
               } else {
                  boolean canAttack = this.getAttackReachSqr(target) >= this.paleHound.distanceToSqr(target);
                  if (canAttack) {
                     return true;
                  } else {
                     path = this.paleHound.getNavigation().createPath(target, 0);
                     return path != null;
                  }
               }
            }
         }
      }
   }

   public boolean canContinueToUse() {
      if (this.paleHound.isSurfaceStalking()) {
         return false;
      }
      LivingEntity target = this.paleHound.getTarget();
      if (this.paleHound.isOnFire() && target instanceof Player && this.paleHound.getRandom().nextDouble() < 0.05) {
         this.paleHound.currentRoll = Roll.FLEE;
         target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 1));
         return super.canContinueToUse();
      } else if (this.paleHound.getHealth() <= this.paleHound.getMaxHealth() * 0.5 && this.paleHound.getRandom().nextDouble() < 0.1) {
         this.paleHound.currentRoll = Roll.FLEE;
         if (target != null) {
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 1));
         }

         return super.canContinueToUse();
      } else if (!Utils.isValidTarget(target)) {
         if (target == null && this.previousTarget instanceof Player) {
            this.paleHound.disappear();
         }

         return false;
      } else {
         this.previousTarget = target;
         return !this.followTargetEvenIfNotSeen && this.paleHound.getNavigation().isDone() ? false : this.paleHound.isWithinRestriction(target.blockPosition());
      }
   }

   public void start() {
      this.paleHound.setAggressive(true);
      this.ticksUntilNextAttack = 0;
   }

   public void stop() {
      LivingEntity target = this.paleHound.getTarget();
      if (!Utils.isValidTarget(target)) {
         this.paleHound.setTarget(null);
      }

      this.speedUp = 0;
      this.previousTarget = null;
      this.paleHound.setAggressive(false);
      this.paleHound.setCrawling(false);
      this.paleHound.getNavigation().stop();
      this.paleHound.refreshDimensions();
   }

   public boolean requiresUpdateEveryTick() {
      return true;
   }

   public void tick() {
      if (this.ticksUntilLeave <= 0 && !this.paleHound.targetIsFacingMe) {
         this.paleHound.disappear();
      }

      LivingEntity target = this.paleHound.getTarget();
      if (Utils.isValidTarget(target)) {
         Path path = this.paleHound.getNavigation().getPath();
         this.fixPath(path);
         boolean targetMoved = path != null && path.getEndNode() != null && path.getEndNode().distanceTo(target.blockPosition()) > 2.0F;
         if (path == null
            || this.paleHound.getNavigation().isStuck()
            || targetMoved
            || path.isDone() && !this.shouldClimb(path)
            || this.paleHound.getNavigation().shouldRecomputePath(target.blockPosition()) && this.paleHound.tickCount % 20 == 0) {
            path = this.paleHound.getNavigation().createPath(target, 0);
            this.fixPath(path);
         }

         if (path != null && !path.isDone() && this.paleHound.hasLineOfSight(target)) {
            this.paleHound.playChaseSound();
         }

         this.paleHound.getNavigation().moveTo(path, this.paleHound.getSpeedModifier());
         if (!this.paleHound.isCrawling()) {
            if (this.paleHound.isAggressive()) {
               this.paleHound.getLookControl().setLookAt(target, 90.0F, 90.0F);
            } else {
               this.paleHound.getLookControl().setLookAt(target, 180.0F, 1.0F);
            }
         }

         this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
         double distance = this.paleHound.distanceToSqr(target);
         this.checkAndPerformAttack(target, distance);
         this.ticksUntilLeave--;
         if (this.speedUp < this.maxSpeedReached) {
            this.speedUp++;
         }
      }
   }

   private void fixPath(Path path) {
      LivingEntity target = this.paleHound.getTarget();
      if (target != null) {
         if (this.shouldClimb(path) && path.getNode(0).distanceTo(this.paleHound.getTarget().blockPosition()) > 0.1) {
            path.replaceNode(0, path.getNode(0).cloneAndMove(target.blockPosition().getX(), target.blockPosition().getY(), target.blockPosition().getZ()));
         }
      }
   }

   private boolean shouldClimb(Path path) {
      return this.paleHound.getTarget() == null
         ? false
         : path != null
            && path.getNodeCount() == 1
            && this.paleHound.getTarget().blockPosition().getY() > this.paleHound.blockPosition().getY() + this.paleHound.maxUpStep();
   }

   private void checkAndPerformAttack(LivingEntity target, double distanceToTarget) {
      double attackReach = this.getAttackReachSqr(target);
      if (distanceToTarget <= attackReach && this.ticksUntilNextAttack <= 0) {
         this.resetAttackCooldown();
         this.paleHound.swing(InteractionHand.MAIN_HAND);
         this.paleHound.doHurtTarget(target);
      }
   }

   private void resetAttackCooldown() {
      this.ticksUntilNextAttack = this.adjustedTickDelay(20);
   }

   private double getAttackReachSqr(LivingEntity target) {
      return this.paleHound.getBbWidth() * 4.0F * this.paleHound.getBbWidth() * 4.0F + target.getBbWidth();
   }
}
