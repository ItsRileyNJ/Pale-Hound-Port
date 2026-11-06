package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.util.Utils;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

public class PaleHoundTargetTooCloseGoal extends NearestAttackableTargetGoal<Player> {
   private final PaleHoundEntity paleHound;
   private final float distanceThreshold;

   public PaleHoundTargetTooCloseGoal(PaleHoundEntity mob, float distanceThreshold) {
      super(mob, Player.class, false);
      this.paleHound = mob;
      this.distanceThreshold = distanceThreshold;
   }

   public boolean canUse() {
      if (this.paleHound.getHuntBrain().blocksLegacyTargeting()) return false;
      if (!this.paleHound.isInvisible()) {
         LivingEntity target = this.paleHound.level().getNearestPlayer(this.paleHound, this.distanceThreshold);
         if (this.paleHound.isValidEncounterTarget(target) && this.paleHound.canSeePlayer((Player)target)) {
            this.target = target;
            return true;
         }
      }

      return false;
   }

   public void start() {
      this.paleHound.setAggressive(true);
      this.paleHound.pickRoll(List.of(Roll.CHASE, Roll.FLEE, Roll.CHASE));
      this.paleHound.setTarget(this.target);
      super.start();
   }

   public void stop() {
      super.stop();
   }

   public boolean canContinueToUse() {
      return this.paleHound.isValidEncounterTarget(this.target);
   }

   public void tick() {
      super.tick();
   }
}
