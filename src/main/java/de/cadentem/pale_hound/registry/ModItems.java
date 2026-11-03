package de.cadentem.pale_hound.registry;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "pale_hound");
   public static final DeferredHolder<Item, Item> PALE_HOUND_SPAWN_EGG = ITEMS.register(
      "pale_hound_spawn_egg", () -> new DeferredSpawnEggItem(ModEntityTypes.PALE_HOUND, 12895428, 790333, new Properties())
   );

   public static void register(IEventBus eventBus) {
      ITEMS.register(eventBus);
   }

   public static void addCreative(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
         event.accept(PALE_HOUND_SPAWN_EGG.get());
      }
   }
}
