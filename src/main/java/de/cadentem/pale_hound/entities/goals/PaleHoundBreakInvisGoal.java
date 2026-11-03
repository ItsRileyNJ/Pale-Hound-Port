package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class PaleHoundBreakInvisGoal extends Goal {
   private final PaleHoundEntity paleHound;

   public PaleHoundBreakInvisGoal(PaleHoundEntity paleHound) {
      this.paleHound = paleHound;
   }

   public boolean canUse() {
      return this.paleHound.isInvisible() && !this.paleHound.targetIsFacingMe;
   }

   public void start() {
      super.start();
      this.paleHound.setInvisible(false);
   }
}
