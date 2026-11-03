package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.client.PaleHoundModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class ClientAssetSmoke {
    private static boolean complete;
    @SubscribeEvent
    public static void checkAssets(ClientTickEvent.Post event) {
        if (complete || !Boolean.getBoolean("pale_hound.clientAssetSmoke")) return;
        var minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        complete = true;
        var model = new PaleHoundModel();
        if (model.getBakedModel(ResourceLocation.fromNamespaceAndPath("pale_hound", "geo/pale_hound.geo.json")) == null)
            throw new AssertionError("Pale Hound model failed to bake");
        for (String animation : new String[]{"new_run", "run_idle", "crouch_run_new", "crouch_idle", "calm_move", "calm_idle", "spotted", "crawl", "crawl_idle", "crawl_low", "crawl_low_idle", "flee"}) {
            if (model.getAnimation(null, "animation.pale_hound." + animation) == null)
                throw new AssertionError("Missing animation: " + animation);
        }
        PaleHound.LOG.info("CLIENT ASSET SMOKE PASSED: model and all 12 used animations loaded");
        minecraft.stop();
    }
}
