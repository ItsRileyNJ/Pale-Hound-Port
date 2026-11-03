package de.cadentem.pale_hound.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.DataGenerator;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "pale_hound",
   bus = Bus.MOD
)
public class DataGen {
   @SubscribeEvent
   public static void configureDataGen(GatherDataEvent event) {
      DataGenerator generator = event.getGenerator();
      ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
      CompletableFuture<Provider> registries = event.getLookupProvider();
      generator.addProvider(event.includeClient(), new ModItemModelProvider(generator.getPackOutput(), "pale_hound", existingFileHelper));
      generator.addProvider(event.includeServer(), new ModBiomeTagsProvider(generator.getPackOutput(), registries, "pale_hound", existingFileHelper));
   }
}
