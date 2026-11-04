package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.client.HoundCueAudio;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.network.HoundSoundCue;
import de.cadentem.pale_hound.network.HoundSoundCue.Kind;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Full decoding plus real packet/OpenAL checks in a disposable integrated world. */
@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class ClientAudioSmoke {
    private static CompletableFuture<Void> decoded;
    private static volatile UUID source;
    private static int phase, ticks, marked;
    private static SoundInstance rejected;
    private static PaleHoundEntity sourceEntity;
    private static CompletableFuture<Void> decodeClips(Minecraft client) {
        var buffers = new SoundBufferLibrary(client.getResourceManager());
        var work = new ArrayList<CompletableFuture<?>>();
        for (String group : new String[]{"ambient_crying", "sees_player", "sees_player_surface", "stalking_underground"}) {
            int count = group.equals("ambient_crying") ? 5 : group.equals("sees_player_surface") ? 2 : 3;
            for (int i = 1; i <= count; ++i) {
                var id = ResourceLocation.fromNamespaceAndPath("pale_hound", "sounds/custom/" + group + "_" + i + ".ogg");
                work.add(buffers.getStream(id, false).thenAccept(stream -> {
                    try (stream) {
                        if (stream.getFormat().getChannels() != 2 || stream.getFormat().getSampleRate() != 48000) throw new AssertionError("Unexpected format: " + id);
                        int bytes = 0;
                        while (true) { var chunk = stream.read(65536); if (!chunk.hasRemaining()) break; bytes += chunk.remaining(); }
                        if (bytes < 48000 * 4 * 5) throw new AssertionError("Clip too short: " + id);
                    } catch (java.io.IOException error) { throw new RuntimeException(id.toString(), error); }
                }));
            }
        }
        return CompletableFuture.allOf(work.toArray(CompletableFuture[]::new));
    }
    private static void send(Kind kind, UUID owner) {
        var client = Minecraft.getInstance(); var id = client.player.getUUID(); var server = client.getSingleplayerServer();
        server.execute(() -> PacketDistributor.sendToPlayer(server.getPlayerList().getPlayer(id), new HoundSoundCue(kind, owner)));
    }
    private static SoundInstance nativeSound() {
        return new SimpleSoundInstance(ResourceLocation.fromNamespaceAndPath("pale_hound", "flee_1"), SoundSource.HOSTILE,
            1, 1, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("pale_hound.clientAudioSmoke")) return;
        var client = Minecraft.getInstance();
        if (++ticks > 3000) throw new AssertionError("Audio smoke timed out in phase " + phase);
        if (phase == 10 && client.level == null) {
            if (HoundCueAudio.activeSoundId() != null) throw new AssertionError("Playback survived logout");
            PaleHound.LOG.info("CLIENT AUDIO SMOKE PASSED: 13 full decodes, exclusive supplied/native channel, time-jump survival, natural completion after stop, entity-untracking survival, logout");
            phase = 11; client.stop(); return;
        }
        if (client.level == null || client.player == null || client.getSingleplayerServer() == null) return;
        client.options.pauseOnLostFocus = false;
        if (phase == 0) {
            decoded = decodeClips(client); var id = client.player.getUUID(); var server = client.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id); player.setGameMode(GameType.CREATIVE);
                var old = new ArrayList<PaleHoundEntity>();
                for (var entity : player.serverLevel().getAllEntities()) if (entity instanceof PaleHoundEntity hound) old.add(hound);
                old.forEach(PaleHoundEntity::discard); source = UUID.randomUUID();
            }); phase = 1;
        } else if (phase == 1 && source != null && decoded.isDone()) {
            decoded.join(); send(Kind.AMBIENT_CRYING, source); phase = 2;
        } else if (phase == 2 && HoundCueAudio.activeKind() == Kind.AMBIENT_CRYING && HoundCueAudio.isPlaying()) {
            client.level.setGameTime(client.level.getGameTime() + 1000); HoundCueAudio.tick(event);
            if (!HoundCueAudio.isPlaying()) throw new AssertionError("Server clock jump truncated crying");
            send(Kind.SEES_PLAYER_SURFACE, source); send(Kind.STALKING_UNDERGROUND, source);
            rejected = nativeSound(); client.getSoundManager().play(rejected); marked = ticks; phase = 3;
        } else if (phase == 3 && ticks > marked + 10) {
            if (HoundCueAudio.activeKind() != Kind.AMBIENT_CRYING || client.getSoundManager().isActive(rejected))
                throw new AssertionError("Native/detection cue overlapped or interrupted crying");
            phase = 4;
        } else if (phase == 4 && HoundCueAudio.activeSoundId() == null) {
            send(Kind.STALKING_UNDERGROUND, source); phase = 5;
        } else if (phase == 5 && HoundCueAudio.activeKind() == Kind.STALKING_UNDERGROUND && HoundCueAudio.isPlaying()) {
            send(Kind.STOP_STALKING, source); send(Kind.STOP_STALKING, UUID.randomUUID()); send(Kind.SEES_PLAYER, source);
            rejected = nativeSound(); client.getSoundManager().play(rejected); marked = ticks; phase = 6;
        } else if (phase == 6 && ticks > marked + 10) {
            if (HoundCueAudio.activeKind() != Kind.STALKING_UNDERGROUND || !HoundCueAudio.isPlaying() || client.getSoundManager().isActive(rejected))
                throw new AssertionError("Ending stalking canceled or overlapped the active clip");
            phase = 7;
        } else if (phase == 7 && HoundCueAudio.activeSoundId() == null) {
            sourceEntity = ModEntityTypes.PALE_HOUND.get().create(client.level); sourceEntity.setPos(client.player.position());
            client.getSoundManager().play(new EntityBoundSoundInstance(BuiltInRegistries.SOUND_EVENT.get(Kind.SEES_PLAYER_SURFACE.soundId()),
                SoundSource.HOSTILE, 1, 1, sourceEntity, 42L)); phase = 8;
        } else if (phase == 8 && HoundCueAudio.isPlaying()) {
            sourceEntity.discard(); marked = ticks; phase = 9;
        } else if (phase == 9 && ticks > marked + 5) {
            if (!HoundCueAudio.isPlaying()) throw new AssertionError("Losing a tracked entity stopped its one-shot sound");
            phase = 10; client.level.disconnect(); client.disconnect();
        }
    }
}
