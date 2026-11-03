package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.client.HoundCueAudio;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.network.HoundSoundCue;
import de.cadentem.pale_hound.network.HoundSoundCue.Kind;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Run only in a disposable integrated world; checks decoding, real packets and the OpenAL playback state. */
@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class ClientAudioSmoke {
    private static CompletableFuture<Void> decoded;
    private static volatile UUID source;
    private static int phase, ticks, marked;

    private static CompletableFuture<Void> decodeClips(Minecraft client) {
        var buffers = new SoundBufferLibrary(client.getResourceManager());
        var work = new ArrayList<CompletableFuture<?>>();
        for (String group : new String[]{"ambient_crying", "sees_player", "sees_player_surface", "stalking_underground"}) {
            int count = group.equals("ambient_crying") ? 5 : group.equals("sees_player_surface") ? 2 : 3;
            for (int i = 1; i <= count; ++i) {
                var id = ResourceLocation.fromNamespaceAndPath("pale_hound", "sounds/custom/" + group + "_" + i + ".ogg");
                work.add(buffers.getStream(id, false).thenAccept(stream -> {
                    try (stream) {
                        if (stream.getFormat().getChannels() != 2 || stream.getFormat().getSampleRate() != 48000)
                            throw new AssertionError("Unexpected audio format: " + id);
                        int bytes = 0;
                        while (true) {
                            var chunk = stream.read(65536);
                            if (!chunk.hasRemaining()) break;
                            bytes += chunk.remaining();
                        }
                        if (bytes < 48000 * 4 * 5) throw new AssertionError("Clip decoded too short: " + id);
                    } catch (java.io.IOException error) { throw new RuntimeException(id.toString(), error); }
                }));
            }
        }
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }

    private static void send(Kind kind, UUID owner) {
        var client = Minecraft.getInstance();
        var playerId = client.player.getUUID();
        var server = client.getSingleplayerServer();
        server.execute(() -> PacketDistributor.sendToPlayer(server.getPlayerList().getPlayer(playerId), new HoundSoundCue(kind, owner)));
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("pale_hound.clientAudioSmoke")) return;
        var client = Minecraft.getInstance();
        if (++ticks > 2400) throw new AssertionError("Audio smoke test timed out in phase " + phase);
        if (phase == 10 && client.level == null) {
            if (HoundCueAudio.activeKind() != null) throw new AssertionError("Playback survived logout");
            PaleHound.LOG.info("CLIENT AUDIO SMOKE PASSED: all 13 clips fully decoded; cue packets, playback, priority, scoped stop and logout verified");
            phase = 11;
            client.stop();
            return;
        }
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        client.options.pauseOnLostFocus = false;
        if (phase == 0) {
            decoded = decodeClips(client);
            var id = client.player.getUUID();
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                player.setGameMode(GameType.CREATIVE);
                var old = new ArrayList<PaleHoundEntity>();
                for (var entity : player.serverLevel().getAllEntities()) if (entity instanceof PaleHoundEntity hound) old.add(hound);
                old.forEach(PaleHoundEntity::discard);
                source = UUID.randomUUID();
            });
            phase = 1;
        } else if (phase == 1 && source != null && decoded.isDone()) {
            decoded.join();
            PaleHound.LOG.info("CLIENT AUDIO: all 13 clips fully decoded; checking packet playback");
            send(Kind.AMBIENT_CRYING, source);
            phase = 2;
        } else if (phase == 2 && HoundCueAudio.activeKind() == Kind.AMBIENT_CRYING && HoundCueAudio.isPlaying()) {
            send(Kind.SEES_PLAYER_SURFACE, source);
            phase = 3;
        } else if (phase == 3 && HoundCueAudio.activeKind() == Kind.SEES_PLAYER_SURFACE && HoundCueAudio.isPlaying()) {
            send(Kind.AMBIENT_CRYING, source);
            send(Kind.STALKING_UNDERGROUND, source);
            marked = ticks;
            phase = 4;
        } else if (phase == 4 && ticks > marked + 10) {
            if (HoundCueAudio.activeKind() != Kind.SEES_PLAYER_SURFACE) throw new AssertionError("Lower-priority clips interrupted player detection");
            phase = 5;
        } else if (phase == 5 && HoundCueAudio.activeKind() == null) {
            send(Kind.STALKING_UNDERGROUND, source);
            phase = 6;
        } else if (phase == 6 && HoundCueAudio.activeKind() == Kind.STALKING_UNDERGROUND && HoundCueAudio.isPlaying()) {
            send(Kind.STOP_STALKING, UUID.randomUUID());
            marked = ticks;
            phase = 7;
        } else if (phase == 7 && ticks > marked + 5) {
            if (HoundCueAudio.activeKind() != Kind.STALKING_UNDERGROUND) throw new AssertionError("Another Hound's stop canceled this clip");
            send(Kind.STOP_STALKING, source);
            phase = 8;
        } else if (phase == 8 && HoundCueAudio.activeKind() == null) {
            send(Kind.SEES_PLAYER, source);
            phase = 9;
        } else if (phase == 9 && HoundCueAudio.activeKind() == Kind.SEES_PLAYER && HoundCueAudio.isPlaying()) {
            phase = 10;
            client.level.disconnect();
            client.disconnect();
        }
    }
}
