package de.cadentem.pale_hound.client;

import de.cadentem.pale_hound.network.HoundSoundCue;
import de.cadentem.pale_hound.network.HoundSoundCue.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/** One Hound sound per listener, including supplied cues, original vocals and footsteps. */
@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class HoundCueAudio {
    private static final class Active {
        final ClientLevel level;
        final Kind kind;
        final SoundInstance sound;
        final long requestedAt = System.nanoTime();
        boolean observedPlaying;
        Active(ClientLevel level, Kind kind, SoundInstance sound) { this.level = level; this.kind = kind; this.sound = sound; }
    }
    private static Active active;
    private HoundCueAudio() {}

    public static boolean handle(HoundSoundCue packet) {
        var client = Minecraft.getInstance();
        if (client.level == null) return false;
        refresh();
        // This ends the server's stalking sequence. Let an already playing clip finish.
        if (packet.kind() == Kind.STOP_STALKING || active != null) return false;
        var sound = new SimpleSoundInstance(packet.kind().soundId(),
            packet.kind() == Kind.AMBIENT_CRYING ? SoundSource.AMBIENT : SoundSource.HOSTILE,
            packet.kind().volume, 1, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        active = new Active(client.level, packet.kind(), sound);
        client.getSoundManager().play(sound);
        return active != null && active.sound == sound;
    }

    /** Catch vanilla entity sound packets and the legacy cave-sound packet as well. */
    @SubscribeEvent public static void play(PlaySoundEvent event) {
        var sound = event.getSound();
        var client = Minecraft.getInstance();
        if (sound == null || client.level == null || !sound.getLocation().getNamespace().equals("pale_hound")) return;
        refresh();
        if (active != null && sound != active.sound) { event.setSound(null); return; }
        if (client.options.getSoundSourceVolume(SoundSource.MASTER) == 0 || client.options.getSoundSourceVolume(sound.getSource()) == 0) {
            if (active != null && sound == active.sound) active = null;
            return;
        }
        if (active != null) return;
        if (sound instanceof EntityBoundSoundInstance) {
            // A one-shot roar must not stop merely because its entity leaves tracking range.
            var original = sound;
            sound = new SimpleSoundInstance(original.getLocation(), original.getSource(), 1, 1,
                RandomSource.create(), false, 0, original.getAttenuation(), original.getX(), original.getY(), original.getZ(), original.isRelative()) {
                // Resolution happens after PlaySoundEvent. Preserve the original chosen sample and gain/pitch.
                @Override public net.minecraft.client.sounds.WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager manager) { return original.resolve(manager); }
                @Override public net.minecraft.client.resources.sounds.Sound getSound() { return original.getSound(); }
                @Override public float getVolume() { return original.getVolume(); }
                @Override public float getPitch() { return original.getPitch(); }
                @Override public boolean canPlaySound() { return original.canPlaySound(); }
            };
            event.setSound(sound);
        }
        active = new Active(client.level, null, sound);
    }

    public static Kind activeKind() { return active == null ? null : active.kind; }
    public static ResourceLocation activeSoundId() { return active == null ? null : active.sound.getLocation(); }
    public static boolean isPlaying() { return active != null && Minecraft.getInstance().getSoundManager().isActive(active.sound); }

    private static void refresh() {
        if (active == null) return;
        var client = Minecraft.getInstance();
        if (client.level != active.level) { clear(); return; }
        if (client.getSoundManager().isActive(active.sound)) active.observedPlaying = true;
        // The audio engine owns completion. Server time jumps and streaming startup cannot truncate a clip.
        else if (active.observedPlaying || System.nanoTime() - active.requestedAt > 10_000_000_000L) clear();
    }

    private static void clear() {
        if (active != null) Minecraft.getInstance().getSoundManager().stop(active.sound);
        active = null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { refresh(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
