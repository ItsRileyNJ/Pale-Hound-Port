package de.cadentem.pale_hound.util;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.SpawnUtil;
import net.minecraft.util.SpawnUtil.Strategy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.common.Tags.Biomes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Utils {
   public static int ticksToSeconds(int ticks) {
      return ticks / 20;
   }

   public static int secondsToTicks(int seconds) {
      return seconds * 20;
   }

   public static int scalePresenceTicks(int ticks) {
      return Math.max(1, (int)Math.ceil(ticks * ServerConfig.PRESENCE_TIME_MULTIPLIER.get()));
   }

   public static int presenceTicks(int seconds) {
      return scalePresenceTicks(secondsToTicks(seconds));
   }

   public static int minutesToTicks(int minutes) {
      return secondsToTicks(minutes * 60);
   }

   public static String getTextureAppend() {
      return "";
   }

   public static boolean isValidTarget(Entity entity) {
      if (entity == null) {
         return false;
      } else if (!entity.isAlive()) {
         return false;
      } else {
         return !ServerConfig.TARGET_INVISIBLE.get() && entity.isInvisible()
            ? false
            : !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
      }
   }

   public static LivingEntity getValidTarget(@NotNull PaleHoundEntity caveDweller) {
      var commanded = caveDweller.getCommandEncounterPlayer();
      if (commanded != null && caveDweller.isValidEncounterTarget(commanded) && caveDweller.distanceToSqr(commanded) <= 128 * 128) return commanded;
      return caveDweller.level()
         .getNearestPlayer(caveDweller.position().x, caveDweller.position().y, caveDweller.position().z, 128.0, Utils::isValidTarget);
   }

   public static boolean isOnSurface(@Nullable Entity entity) {
      if (entity == null) {
         return false;
      } else {
         if (entity.level() instanceof ServerLevel serverLevel) {
            BlockPos blockPosition = entity.blockPosition();
            if (serverLevel.canSeeSky(blockPosition)) {
               return true;
            }

            Holder<Biome> biome = serverLevel.getBiome(blockPosition);
            if (biome.is(Biomes.IS_CAVE) || biome.is(Biomes.IS_UNDERGROUND)) {
               return false;
            }

            int baseSkyLightLevel = serverLevel.getBrightness(LightLayer.SKY, blockPosition) - serverLevel.getSkyDarken();
            if (baseSkyLightLevel > 0) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean moveToPossibleSpawnPosition(ServerLevel level, int verticalRange, MutableBlockPos position, Strategy strategy) {
      MutableBlockPos below = position.mutable();
      var aboveState = level.getBlockState(below);
      for (int offset = verticalRange; offset >= -verticalRange; --offset) {
         below.move(net.minecraft.core.Direction.DOWN);
         var belowState = level.getBlockState(below);
         if (strategy.canSpawnOn(level, below, belowState, position, aboveState)) {
            position.set(below).move(net.minecraft.core.Direction.UP);
            return true;
         }
         position.set(below);
         aboveState = belowState;
      }
      return false;
   }

   public static <T extends Mob> Optional<T> trySpawnMob(
      @NotNull Entity currentVictim,
      EntityType<T> entityType,
      MobSpawnType spawnType,
      ServerLevel level,
      BlockPos blockPosition,
      int attempts,
      int xzOffset,
      int yOffset,
      Strategy strategy
   ) {
      if (de.cadentem.pale_hound.difficulty.StoryDifficulty.isEnabled(level.getServer())
         && entityType.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER
         && !de.cadentem.pale_hound.difficulty.StoryDifficulty.isAllowed(entityType)) return Optional.empty();
      MutableBlockPos mutableBlockPosition = blockPosition.mutable();

      for (int i = 0; i < attempts; i++) {
         int xOffset = Mth.randomBetweenInclusive(level.random, -xzOffset, xzOffset);
         int zOffset = Mth.randomBetweenInclusive(level.random, -xzOffset, xzOffset);
         mutableBlockPosition.setWithOffset(blockPosition, xOffset, yOffset, zOffset);
         if (level.getWorldBorder().isWithinBounds(mutableBlockPosition) && moveToPossibleSpawnPosition(level, yOffset, mutableBlockPosition, strategy)) {
            if (!PaleHoundBiomes.isAllowed(level.getBiome(mutableBlockPosition))) continue;
            T entity = (T)entityType.create(level, null, mutableBlockPosition, spawnType, false, false);
            if (entity instanceof PaleHoundEntity) {
               if (entity.checkSpawnRules(level, spawnType) && entity.checkSpawnObstruction(level)) {
                  boolean isValidSpawn = entity.level().getNearestPlayer(entity, Math.max(ServerConfig.SPAWN_DISTANCE.get(), Utils.isOnSurface(currentVictim) ? 32 : 0)) == null;
                  if (isValidSpawn && (Boolean)ServerConfig.CHECK_PATH_TO_SPAWN.get()) {
                     Path path = entity.getNavigation().createPath(currentVictim, 0);
                     isValidSpawn = path != null && path.canReach();
                  }

                  if (isValidSpawn) {
                     entity.getNavigation().createPath(entity.blockPosition(), 0);
                     entity.getNavigation().stop();
                     if (level.addFreshEntity(entity)) {
                        entity.getPassengers().forEach(level::addFreshEntityWithPassengers);
                        return Optional.of(entity);
                     }
                  }
               }

               entity.discard();
            }
         }
      }

      PaleHound.LOG.debug("Cave Dweller could not pass the spawn checks, target: [{}]", currentVictim);
      return Optional.empty();
   }
}
