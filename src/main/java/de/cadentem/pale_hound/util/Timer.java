package de.cadentem.pale_hound.util;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.config.ServerConfig;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.Entity;

public class Timer {
   @Nullable
   public Entity currentVictim;
   public int currentSpawn;
   public int currentNoise;
   public int targetSpawn;
   public int targetNoise;

   public Timer() {
      this.resetSpawnTimer();
      this.resetNoiseTimer();
   }

   public Timer(@Nullable Entity currentVictim) {
      this.currentVictim = currentVictim;
   }

   public boolean isSpawnTimerReached() {
      return this.currentSpawn >= this.getEffectiveSpawnTarget();
   }

   public boolean isNoiseTimerReached() {
      return this.currentNoise >= this.getEffectiveNoiseTarget();
   }

   public int getEffectiveSpawnTarget() {
      return Utils.isOnSurface(this.currentVictim)
         ? (int)(this.targetSpawn * ServerConfig.SURFACE_TIMER_MULTIPLIER.get() * ServerConfig.SURFACE_SPAWN_TIMER_MULTIPLIER.get())
         : this.targetSpawn;
   }

   public int getEffectiveNoiseTarget() {
      return Utils.isOnSurface(this.currentVictim)
         ? (int)(this.targetNoise * ServerConfig.SURFACE_TIMER_MULTIPLIER.get()) : this.targetNoise;
   }

   public void resetNoiseTimer() {
      int min = (Integer)ServerConfig.RESET_NOISE_MIN.get();
      int max = (Integer)ServerConfig.RESET_NOISE_MAX.get();
      if (max < min) {
         int temp = min;
         min = max;
         max = temp;
         PaleHound.LOG
            .error("Configuration for `RESET_NOISE` was wrong - max [{}] was smaller than min [{}] - values have been switched to prevent a crash", temp, min);
      }

      int noiseTimer = PaleHound.RANDOM.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));
      this.currentNoise = 0;
      this.targetNoise = noiseTimer;
   }

   public void resetSpawnTimer() {
      int spawnTimer;
      if (PaleHound.RANDOM.nextDouble() <= (Double)ServerConfig.CAN_SPAWN_COOLDOWN_CHANCE.get()) {
         spawnTimer = Utils.secondsToTicks((Integer)ServerConfig.CAN_SPAWN_COOLDOWN.get());
      } else {
         int min = (Integer)ServerConfig.CAN_SPAWN_MIN.get();
         int max = (Integer)ServerConfig.CAN_SPAWN_MAX.get();
         if (max < min) {
            int temp = min;
            min = max;
            max = temp;
            PaleHound.LOG
               .error("Configuration for `RESET_CALM` was wrong - max [{}] was smaller than min [{}] - values have been switched to prevent a crash", temp, min);
         }

         spawnTimer = PaleHound.RANDOM.nextInt(Utils.secondsToTicks(min), Utils.secondsToTicks(max + 1));
      }

      this.currentSpawn = 0;
      this.targetSpawn = (int)(spawnTimer * ServerConfig.SPAWN_TIMER_MULTIPLIER.get());
   }

   public void resetSpawnTimerAfterBeingSpotted() {
      this.resetSpawnTimer();
      this.targetSpawn = (int)Math.ceil(this.targetSpawn * 0.25);
   }

   @Override
   public String toString() {
      String name = this.currentVictim != null ? this.currentVictim.getName().getString() : "NONE";
      return name + " | " + this.currentSpawn + "/" + this.targetSpawn + " | " + this.currentNoise + "/" + this.targetNoise;
   }
}
