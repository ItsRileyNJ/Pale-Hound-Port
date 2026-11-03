package de.cadentem.pale_hound;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.network.NetworkHandler;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import de.cadentem.pale_hound.registry.ModItems;
import de.cadentem.pale_hound.registry.ModSounds;
import de.cadentem.pale_hound.util.Timer;
import de.cadentem.pale_hound.util.Utils;
import de.cadentem.pale_hound.util.PaleHoundBiomes;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.SpawnUtil.Strategy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Mod("pale_hound")
public class PaleHound {
   public static final String MODID = "pale_hound";
   public static final Logger LOG = LogUtils.getLogger();
   public static final Random RANDOM = new Random();
   private static final HashMap<String, Timer> TIMERS = new HashMap<>();
   public static boolean RELOAD_ALL = false;
   public static boolean RELOAD_MISSING = false;

   public PaleHound(IEventBus modEventBus, ModContainer container) {
      modEventBus.addListener(NetworkHandler::register);
      modEventBus.addListener(ModItems::addCreative);
      ModItems.register(modEventBus);
      ModSounds.register(modEventBus);
      ModEntityTypes.register(modEventBus);
      NeoForge.EVENT_BUS.register(this);
      container.registerConfig(Type.SERVER, ServerConfig.SPEC);
   }

   @SubscribeEvent
   public void serverStartup(ServerStartedEvent event) {
      RELOAD_ALL = true;
   }

   @SubscribeEvent
   public void serverTick(ServerTickEvent.Pre event) {
      {
         if (RELOAD_ALL) {
            TIMERS.clear();
            RELOAD_ALL = false;
         }

         Iterable<ServerLevel> levels = event.getServer().getAllLevels();
         if (TIMERS.isEmpty()) {
            for (ServerLevel level : levels) {
               String key = level.dimension().location().toString();
               boolean isRelevant = ((List)ServerConfig.DIMENSION_WHITELIST.get()).contains(key);
               if (isRelevant) {
                  TIMERS.put(key, new Timer());
               }
            }

            if (TIMERS.isEmpty() && event.getServer().getTickCount() % 6000 == 0) {
               LOG.debug("There are currently no timers present - are the dimensions properly configured?");
            }

            RELOAD_ALL = false;
            RELOAD_MISSING = false;
         } else if (RELOAD_MISSING) {
            for (ServerLevel levelx : levels) {
               String key = levelx.dimension().location().toString();
               boolean isRelevant = TIMERS.get(key) == null && ((List)ServerConfig.DIMENSION_WHITELIST.get()).contains(key);
               if (isRelevant) {
                  TIMERS.put(key, new Timer());
               }
            }

            RELOAD_MISSING = false;
         }

         for (ServerLevel levelxx : levels) {
            String key = levelxx.dimension().location().toString();
            if (TIMERS.get(key) != null) {
               this.handleLogic(levelxx);
            }
         }
      }
   }

   private void handleLogic(ServerLevel level) {
      if (level != null) {
         List<ServerPlayer> players = level.getPlayers(this::isRelevantPlayer);
         if (!players.isEmpty()) {
            String key = level.dimension().location().toString();
            Timer timer = TIMERS.get(key);
            if (timer.currentVictim == null || players.stream().filter(element -> element.getStringUUID().equals(timer.currentVictim.getStringUUID())).toList().isEmpty()
               )
             {
               timer.currentVictim = (Entity)players.get(RANDOM.nextInt(players.size()));
            }

            Iterable<Entity> entities = level.getAllEntities();
            AtomicInteger paleHoundCount = new AtomicInteger();
            entities.forEach(entity -> {
               if (entity instanceof PaleHoundEntity) {
                  paleHoundCount.getAndAdd(1);
               }
            });
            timer.currentSpawn++;
            timer.currentNoise++;
            if (timer.isNoiseTimerReached()
               && (paleHoundCount.get() > 0 || timer.currentSpawn >= Utils.secondsToTicks((Integer)ServerConfig.CAN_SPAWN_MAX.get()) / 2)) {
               this.playCaveSoundToSpelunkers(players, timer);
            }

            if (timer.isSpawnTimerReached()
               && paleHoundCount.get() < (Integer)ServerConfig.MAXIMUM_AMOUNT.get()
               && RANDOM.nextDouble() < PaleHoundBiomes.spawnChance(timer.currentVictim)
               && timer.currentVictim != null) {
               Optional<PaleHoundEntity> optionalEntity = Utils.trySpawnMob(
                  timer.currentVictim,
                  ModEntityTypes.PALE_HOUND.get(),
                  MobSpawnType.TRIGGERED,
                  level,
                  timer.currentVictim.blockPosition(),
                  40,
                  35,
                  6,
                  Strategy.ON_TOP_OF_COLLIDER
               );
               if (optionalEntity.isPresent()) {
                  this.playCaveSoundToSpelunkers(players, timer);
                  PaleHoundEntity paleHound = optionalEntity.get();
                  paleHound.setInvisible(true);
                  paleHound.hasSpawned = true;
                  timer.resetSpawnTimer();
               } else {
                  timer.currentVictim = null;
               }
            }
         }
      }
   }

   private void playCaveSoundToSpelunkers(List<ServerPlayer> players, Timer timer) {
      Entity currentVictim = timer.currentVictim;
      if (currentVictim != null) {
         players.forEach(player -> {
            if (!(Boolean)ServerConfig.ONLY_PLAY_NOISE_TO_TARGET.get() || player.is(timer.currentVictim)) {
               PacketDistributor.sendToPlayer(player, new de.cadentem.pale_hound.network.HoundSoundCue(
                     de.cadentem.pale_hound.network.HoundSoundCue.Kind.AMBIENT_CRYING, new java.util.UUID(0, 0)));
            }
         });
         timer.resetNoiseTimer();
      }
   }

   private boolean isRelevantPlayer(ServerPlayer player) {
      if (!Utils.isValidTarget(player)) {
         return false;
      } else if (player.position().y > ((Integer)ServerConfig.SPAWN_HEIGHT.get()).intValue()) {
         return false;
      } else {
         Level serverLevel = player.level();
         if (!PaleHoundBiomes.isAllowed(serverLevel.getBiome(player.blockPosition()))) return false;
         int actualSkyLightLevel = serverLevel.getBrightness(LightLayer.SKY, player.blockPosition()) - serverLevel.getSkyDarken();
         float sunAngle = serverLevel.getSunAngle(1.0F);
         if (actualSkyLightLevel > 0) {
            float f1 = sunAngle < (float) Math.PI ? 0.0F : (float) (Math.PI * 2);
            sunAngle += (f1 - sunAngle) * 0.2F;
            actualSkyLightLevel = Math.round(actualSkyLightLevel * Mth.cos(sunAngle));
         }

         actualSkyLightLevel = Mth.clamp(actualSkyLightLevel, 0, 15);
         if (actualSkyLightLevel > (Integer)ServerConfig.SKY_LIGHT_LEVEL.get()) {
            return false;
         } else {
            LayerLightEventListener blockLighting = serverLevel.getLightEngine().getLayerListener(LightLayer.BLOCK);
            if (blockLighting.getLightValue(player.blockPosition()) > (Integer)ServerConfig.BLOCK_LIGHT_LEVEL.get()) {
               return false;
            } else {
               boolean isOnSurface = Utils.isOnSurface(player);
               if (isOnSurface) {
                  return !ServerConfig.ALLOW_SURFACE_SPAWN.get() ? false : ServerConfig.isInValidBiome(player);
               } else {
                  return true;
               }
            }
         }
      }
   }

   public static void scheduleSpawnAfterSpottedRetreat(ServerLevel level) {
      String key = level.dimension().location().toString();
      if (!ServerConfig.isValidDimension(key)) return;
      Timer timer = TIMERS.computeIfAbsent(key, ignored -> new Timer());
      timer.resetSpawnTimerAfterBeingSpotted();
   }

   public static boolean speedUpTimers(String key, int spawnDelta, int noiseDelta) {
      Timer timer = TIMERS.get(key);
      LOG.debug("Speeding up timers for the dimension [{}], timer: [{}]", key, timer);
      if (timer != null) {
         timer.currentSpawn += spawnDelta;
         timer.currentNoise += noiseDelta;
         return true;
      } else {
         return false;
      }
   }

   public static Pair<Integer, Integer> getTimer(String key, String type) {
      return getTimer(key, type, false);
   }

   public static Pair<Integer, Integer> getEffectiveTimer(String key, String type) {
      return getTimer(key, type, true);
   }

   private static Pair<Integer, Integer> getTimer(String key, String type, boolean effective) {
      Timer timer = TIMERS.get(key);
      int current = -1;
      int target = -1;
      if (timer != null) {
         switch (type) {
            case "spawn":
               current = timer.currentSpawn;
               target = effective ? timer.getEffectiveSpawnTarget() : timer.targetSpawn;
               break;
            case "noise":
               current = timer.currentNoise;
               target = effective ? timer.getEffectiveNoiseTarget() : timer.targetNoise;
         }
      }

      return Pair.of(current, target);
   }

   @Nullable
   public static Entity getCurrentVictim(String key) {
      Timer timer = TIMERS.get(key);
      return timer != null ? timer.currentVictim : null;
   }
}
