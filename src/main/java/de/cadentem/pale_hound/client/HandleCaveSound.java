package de.cadentem.pale_hound.client;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.network.CaveSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.core.registries.BuiltInRegistries;

public class HandleCaveSound {
   public static void handle(CaveSound packet) {
      var soundEvents = BuiltInRegistries.SOUND_EVENT;
      if (soundEvents == null) {
         PaleHound.LOG.error("Forge Sound registry was null while handling packet");
      } else {
         SoundEvent soundEvent = (SoundEvent)soundEvents.get(packet.soundResource);
         if (soundEvent == null) {
            PaleHound.LOG.error("Sound Event [" + packet.soundResource + "] was null while handling packet");
         } else {
            Minecraft.getInstance()
               .getSoundManager()
               .play(new SimpleSoundInstance(soundEvent, SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), packet.playerPosition));
         }
      }
   }
}
