package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

public class PaleHoundStrollGoal extends WaterAvoidingRandomStrollGoal {
   public PaleHoundStrollGoal(PaleHoundEntity mob, double speedModifier) {
      super(mob, speedModifier);
   }

   public boolean canUse() {
      PaleHoundEntity paleHound = (PaleHoundEntity)this.mob;
      if (paleHound.currentRoll == Roll.STROLL && paleHound.getHealth() <= paleHound.getMaxHealth() * 0.5) {
         paleHound.currentRoll = Roll.FLEE;
         return true;
      } else {
         return paleHound.currentRoll == Roll.STROLL && super.canUse();
      }
   }

   public boolean canContinueToUse() {
      PaleHoundEntity paleHound = (PaleHoundEntity)this.mob;
      return paleHound.currentRoll == Roll.STROLL && super.canContinueToUse();
   }
}
