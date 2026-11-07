package de.cadentem.pale_hound.client;

import de.cadentem.pale_hound.registry.ModEntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Keep renderer class loading on the physical client. */
@EventBusSubscriber(modid = "pale_hound", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.PALE_HOUND.get(), PaleHoundRenderer::new);
    }
}
