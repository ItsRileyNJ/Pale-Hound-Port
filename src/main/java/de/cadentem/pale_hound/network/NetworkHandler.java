package de.cadentem.pale_hound.network;

import de.cadentem.pale_hound.client.HandleCaveSound;
import de.cadentem.pale_hound.client.HoundHighlights;
import de.cadentem.pale_hound.client.HoundCueAudio;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3").playToClient(StoryDifficultyState.TYPE, StoryDifficultyState.STREAM_CODEC,
                (packet, context) -> de.cadentem.pale_hound.client.StoryDifficultyClient.handle(packet));
        event.registrar("3").playToServer(StoryDifficultyRequest.TYPE, StoryDifficultyRequest.STREAM_CODEC,
                (packet, context) -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player
                        && de.cadentem.pale_hound.difficulty.StoryDifficulty.mayUseSelector(player))
                        de.cadentem.pale_hound.difficulty.StoryDifficulty.setEnabled(player.getServer(), true);
                });
        // NeoForge dispatches payload handlers on the main thread by default.
        event.registrar("3").playToClient(CaveSound.TYPE, CaveSound.STREAM_CODEC,
                (packet, context) -> HandleCaveSound.handle(packet));
        event.registrar("3").playToClient(HoundHighlight.TYPE, HoundHighlight.STREAM_CODEC,
                (packet, context) -> HoundHighlights.handle(packet));
        event.registrar("3").playToClient(HoundSoundCue.TYPE, HoundSoundCue.STREAM_CODEC,
                (packet, context) -> HoundCueAudio.handle(packet));
    }
}
