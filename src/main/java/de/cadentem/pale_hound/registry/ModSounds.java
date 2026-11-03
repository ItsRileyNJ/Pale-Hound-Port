package de.cadentem.pale_hound.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModSounds {
   public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, "pale_hound");
   public static final DeferredHolder<SoundEvent, SoundEvent> CAVENOISE_1 = registerSoundEvent("cavenoise_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> CAVENOISE_2 = registerSoundEvent("cavenoise_2");
   public static final DeferredHolder<SoundEvent, SoundEvent> CAVENOISE_3 = registerSoundEvent("cavenoise_3");
   public static final DeferredHolder<SoundEvent, SoundEvent> CAVENOISE_4 = registerSoundEvent("cavenoise_4");
   public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_WHINE = registerSoundEvent("wolf_whine");
   public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_PANTING = registerSoundEvent("wolf_panting");
   public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_IDLE1 = registerSoundEvent("wolf_idle1");
   public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_IDLE2 = registerSoundEvent("wolf_idle2");
   public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_IDLE3 = registerSoundEvent("wolf_idle3");
   public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_HOWL1 = registerSoundEvent("wolf_howl1");
   public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_HOWL2 = registerSoundEvent("wolf_howl2");
   public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_STEP_1 = registerSoundEvent("chase_step_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_STEP_2 = registerSoundEvent("chase_step_2");
   public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_STEP_3 = registerSoundEvent("chase_step_3");
   public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_STEP_4 = registerSoundEvent("chase_step_4");
   public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_1 = registerSoundEvent("chase_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_2 = registerSoundEvent("chase_2");
   public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_3 = registerSoundEvent("chase_3");
   public static final DeferredHolder<SoundEvent, SoundEvent> CHASE_4 = registerSoundEvent("chase_4");
   public static final DeferredHolder<SoundEvent, SoundEvent> FLEE_1 = registerSoundEvent("flee_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> FLEE_2 = registerSoundEvent("flee_2");
   public static final DeferredHolder<SoundEvent, SoundEvent> SPOTTED = registerSoundEvent("spotted");
   public static final DeferredHolder<SoundEvent, SoundEvent> DISAPPEAR = registerSoundEvent("disappear");
   public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_HURT_1 = registerSoundEvent("hound_hurt_1");
   public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_HURT_2 = registerSoundEvent("hound_hurt_2");
   public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_HURT_3 = registerSoundEvent("hound_hurt_3");
   public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_HURT_4 = registerSoundEvent("hound_hurt_4");
   public static final DeferredHolder<SoundEvent, SoundEvent> HOUND_DEATH = registerSoundEvent("hound_death");

   private static DeferredHolder<SoundEvent, SoundEvent> registerSoundEvent(String name) {
      ResourceLocation id = ResourceLocation.fromNamespaceAndPath("pale_hound", name);
      return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
   }

   public static void register(IEventBus eventBus) {
      SOUND_EVENTS.register(eventBus);
   }
}
