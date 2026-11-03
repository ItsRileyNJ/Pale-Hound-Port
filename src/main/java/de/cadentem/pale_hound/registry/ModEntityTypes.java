package de.cadentem.pale_hound.registry;

import de.cadentem.pale_hound.entities.PaleHoundEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModEntityTypes {
   public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "pale_hound");
   public static final DeferredHolder<EntityType<?>, EntityType<PaleHoundEntity>> PALE_HOUND = ENTITY_TYPES.register(
      "pale_hound",
      () -> Builder.of(PaleHoundEntity::new, MobCategory.MONSTER)
         .sized(0.5F, 2.7F)
         .build(ResourceLocation.fromNamespaceAndPath("pale_hound", "pale_hound").toString())
   );

   public static void register(IEventBus eventBus) {
      ENTITY_TYPES.register(eventBus);
   }
}
