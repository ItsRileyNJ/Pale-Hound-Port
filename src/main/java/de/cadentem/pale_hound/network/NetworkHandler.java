package de.cadentem.pale_hound.network;

import de.cadentem.pale_hound.client.HandleCaveSound;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        // NeoForge dispatches payload handlers on the main thread by default.
        event.registrar("2").playToClient(CaveSound.TYPE, CaveSound.STREAM_CODEC,
                (packet, context) -> HandleCaveSound.handle(packet));
    }
}
