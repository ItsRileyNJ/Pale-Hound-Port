package de.cadentem.pale_hound.events;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.config.ModConfigEvent.Reloading;
import net.neoforged.fml.event.config.ModConfigEvent.Loading;

@EventBusSubscriber(
   modid = "pale_hound",
   bus = Bus.MOD
)
public class ModEvents {
   @SubscribeEvent
   public static void entityAttributeEvent(EntityAttributeCreationEvent event) {
      event.put((EntityType)ModEntityTypes.PALE_HOUND.get(), PaleHoundEntity.getAttributeBuilder());
   }

   @SubscribeEvent
   public static void loadConfiguration(Loading event) {
      if (event.getConfig().getSpec() == ServerConfig.SPEC) migrateBiomeConfig();
   }

   private static void migrateBiomeConfig() {
      if (ServerConfig.migrateDefaultBiomeBlacklist()) {
         ServerConfig.SPEC.save();
         PaleHound.LOG.info("Updated the original surface biome blacklist: only deserts and badlands/Mesa remain excluded");
      }
   }

   @SubscribeEvent
   public static void reloadConfiguration(Reloading event) {
      if (event.getConfig().getSpec() == ServerConfig.SPEC) {
         migrateBiomeConfig();
         PaleHound.RELOAD_ALL = true;
         var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
         if (server != null) server.execute(() -> de.cadentem.pale_hound.difficulty.StoryDifficulty.apply(server));
         PaleHound.LOG.info("Server configuration has been reloaded");
      }
   }
}
