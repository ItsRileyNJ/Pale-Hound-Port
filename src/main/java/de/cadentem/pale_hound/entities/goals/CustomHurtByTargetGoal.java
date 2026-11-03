package de.cadentem.pale_hound.entities.goals;

import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;

public class CustomHurtByTargetGoal extends HurtByTargetGoal {
   public CustomHurtByTargetGoal(PathfinderMob mob, Class<?>... toIgnoreDamage) {
      super(mob, toIgnoreDamage);
   }

   public boolean canUse() {
      return this.mob instanceof PaleHoundEntity caveDweller && (caveDweller.currentRoll == Roll.CHASE || caveDweller.currentRoll == Roll.FLEE)
         ? this.mob.getRandom().nextDouble() < 0.2 && super.canUse()
         : super.canUse();
   }

   public void start() {
      super.start();
      if (this.mob.getLastHurtByMob() instanceof Player) {
         this.unseenMemoryTicks = (Integer)ServerConfig.TIME_UNTIL_LEAVE.get();
      } else {
         this.unseenMemoryTicks = 300;
      }
   }
}
