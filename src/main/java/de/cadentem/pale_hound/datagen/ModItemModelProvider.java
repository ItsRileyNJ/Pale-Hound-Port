package de.cadentem.pale_hound.datagen;

import de.cadentem.pale_hound.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
   public ModItemModelProvider(PackOutput packOutput, String modId, ExistingFileHelper existingFileHelper) {
      super(packOutput, modId, existingFileHelper);
   }

   protected void registerModels() {
      this.withExistingParent(ModItems.PALE_HOUND_SPAWN_EGG.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
   }
}
