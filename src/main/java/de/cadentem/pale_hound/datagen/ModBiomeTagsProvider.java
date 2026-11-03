package de.cadentem.pale_hound.datagen;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags.Biomes;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModBiomeTagsProvider extends BiomeTagsProvider {
   public static TagKey<Biome> PALE_HOUND_SURFACE_BIOMES = TagKey.create(
      Registries.BIOME, ResourceLocation.fromNamespaceAndPath("pale_hound", "pale_hound_surface_biomes")
   );

   public ModBiomeTagsProvider(PackOutput packOutput, CompletableFuture<Provider> registries, String modId, @Nullable ExistingFileHelper existingFileHelper) {
      super(packOutput, registries, modId, existingFileHelper);
   }

   protected void addTags(@NotNull Provider provider) {
      this.tag(PALE_HOUND_SURFACE_BIOMES).addOptionalTag(Biomes.IS_SPOOKY.location());
      this.tag(de.cadentem.pale_hound.util.PaleHoundBiomes.BLOCKED)
         .add(net.minecraft.world.level.biome.Biomes.BADLANDS, net.minecraft.world.level.biome.Biomes.WOODED_BADLANDS,
            net.minecraft.world.level.biome.Biomes.ERODED_BADLANDS, net.minecraft.world.level.biome.Biomes.DESERT)
         .addOptionalTag(net.minecraft.tags.BiomeTags.IS_BADLANDS.location())
         .addOptionalTag(Biomes.IS_BADLANDS.location()).addOptionalTag(Biomes.IS_DESERT.location());
      this.tag(de.cadentem.pale_hound.util.PaleHoundBiomes.FORESTED)
         .addOptionalTag(net.minecraft.tags.BiomeTags.IS_FOREST.location())
         .addOptionalTag(net.minecraft.tags.BiomeTags.IS_TAIGA.location())
         .addOptionalTag(net.minecraft.tags.BiomeTags.IS_JUNGLE.location())
         .addOptionalTag(Biomes.IS_FOREST.location()).addOptionalTag(Biomes.IS_TAIGA.location())
         .addOptionalTag(Biomes.IS_JUNGLE.location()).addOptionalTag(Biomes.IS_DECIDUOUS_TREE.location())
         .addOptionalTag(Biomes.IS_CONIFEROUS_TREE.location()).addOptionalTag(Biomes.IS_JUNGLE_TREE.location())
         .add(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP);
   }
}
