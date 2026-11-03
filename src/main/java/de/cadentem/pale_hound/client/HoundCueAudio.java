package de.cadentem.pale_hound.client;

import de.cadentem.pale_hound.network.HoundSoundCue;
import de.cadentem.pale_hound.network.HoundSoundCue.Kind;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** One supplied playback clip at a time, preserving stereo and respecting the client's sound sliders. */
@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class HoundCueAudio {
    private record Active(ClientLevel level, UUID source, Kind kind, SoundInstance sound, long started) {}
    private static Active active;
    private HoundCueAudio() {}

    public static boolean handle(HoundSoundCue packet) {
        var client = Minecraft.getInstance();
        if (client.level == null) return false;
        refresh();
        if (packet.kind() == Kind.STOP_STALKING) {
            if (active != null && active.kind == Kind.STALKING_UNDERGROUND && active.source.equals(packet.source())) clear();
            return false;
        }
        if (active != null && packet.kind().priority <= active.kind.priority) return false;
        clear();
        var sound = new SimpleSoundInstance(packet.kind().soundId(),
            packet.kind() == Kind.AMBIENT_CRYING ? SoundSource.AMBIENT : SoundSource.HOSTILE,
            packet.kind().volume, 1, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        active = new Active(client.level, packet.source(), packet.kind(), sound, client.level.getGameTime());
        client.getSoundManager().play(sound);
        return true;
    }

    public static Kind activeKind() { return active == null ? null : active.kind; }
    public static boolean isPlaying() { return active != null && Minecraft.getInstance().getSoundManager().isActive(active.sound); }

    private static void refresh() {
        if (active == null) return;
        var client = Minecraft.getInstance();
        long age = active.level.getGameTime() - active.started;
        // Longest supplied clip is 14.4 seconds. Allow startup time for asynchronous streaming.
        if (client.level != active.level || age >= 300 || age > 20 && !client.getSoundManager().isActive(active.sound)) clear();
    }

    private static void clear() {
        if (active != null) Minecraft.getInstance().getSoundManager().stop(active.sound);
        active = null;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { refresh(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
