package de.cadentem.pale_hound.network;

import de.cadentem.pale_hound.client.HandleCaveSound;
import de.cadentem.pale_hound.client.HoundHighlights;
import de.cadentem.pale_hound.client.HoundCueAudio;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // NeoForge dispatches payload handlers on the main thread by default.
        event.registrar("2").playToClient(CaveSound.TYPE, CaveSound.STREAM_CODEC,
                (packet, context) -> HandleCaveSound.handle(packet));
        event.registrar("2").playToClient(HoundHighlight.TYPE, HoundHighlight.STREAM_CODEC,
                (packet, context) -> HoundHighlights.handle(packet));
        event.registrar("2").playToClient(HoundSoundCue.TYPE, HoundSoundCue.STREAM_CODEC,
                (packet, context) -> HoundCueAudio.handle(packet));
    }
}
