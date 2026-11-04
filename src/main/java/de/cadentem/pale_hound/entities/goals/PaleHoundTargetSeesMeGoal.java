package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.util.Utils;
import java.util.List;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

public class PaleHoundTargetSeesMeGoal extends NearestAttackableTargetGoal<Player> {
   private final PaleHoundEntity caveDweller;

   public PaleHoundTargetSeesMeGoal(PaleHoundEntity mob) {
      super(mob, Player.class, false);
      this.caveDweller = mob;
   }

   public boolean canUse() {
      if (this.caveDweller.isInvisible()) {
         return false;
      } else {
         this.target = Utils.getValidTarget(this.caveDweller);
         return !this.caveDweller.isValidEncounterTarget(this.target) ? false : this.caveDweller.isLookingAtMe(this.target, true);
      }
   }

   public void start() {
      this.caveDweller.setTarget(this.target);
      this.caveDweller.setSpotted(true);
      if (this.target != null) {
         this.caveDweller.pickRoll(List.of(Roll.CHASE, Roll.STARE, Roll.STARE, Roll.STARE, Roll.FLEE));
      }

      super.start();
   }

   public void stop() {
      super.stop();
   }

   public boolean canContinueToUse() {
      return this.caveDweller.isValidEncounterTarget(this.target);
   }

   public void tick() {
      super.tick();
   }
}
