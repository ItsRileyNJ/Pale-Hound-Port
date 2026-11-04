package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.client.PaleHoundModel;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;

/** Real GeckoLib asset/controller checks on the physical client, in a disposable world. */
@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class ClientAnimationSmoke {
    private static boolean complete;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws ReflectiveOperationException {
        var client = Minecraft.getInstance();
        if (complete || !Boolean.getBoolean("pale_hound.clientAnimationSmoke") || client.level == null || client.player == null) return;
        complete = true;
        var forced = new PaleHoundEntity.CrawlPose[]{PaleHoundEntity.CrawlPose.TALL};
        PaleHoundEntity hound = new PaleHoundEntity(ModEntityTypes.PALE_HOUND.get(), client.level) {
            @Override public CrawlPose getCrawlPose() { return forced[0]; }
        };
        hound.setPos(client.player.position());
        var model = new PaleHoundModel();
        if (model.getBakedModel(model.getModelResource(hound)) == null) throw new AssertionError("Model failed to bake");
        var controller = hound.getAnimatableInstanceCache().<PaleHoundEntity>getManagerForId(hound.getId()).getAnimationControllers().get("controller");
        // The renderer normally supplies this model before invoking the state handler.
        var lastModel = AnimationController.class.getDeclaredField("lastModel"); lastModel.setAccessible(true); lastModel.set(controller, model);
        var transition = AnimationController.class.getDeclaredField("transitionLength"); transition.setAccessible(true);
        if (transition.getDouble(controller) != 12) throw new AssertionError("Posture blending is not 12 ticks");
        var standing = new AnimationState<>(hound, 0, 0, 0, false).withController(controller);
        controller.getStateHandler().handle(standing);
        if (Math.abs(controller.getAnimationSpeed() - 0.75) > 0.00001) throw new AssertionError("Standing animation slowdown missing");
        hound.setCrawling(true); hound.setBackingAway(true); hound.setDeltaMovement(new Vec3(0.02, 0, 0));
        for (var pose : PaleHoundEntity.CrawlPose.values()) {
            forced[0] = pose;
            var moving = new AnimationState<>(hound, 1, 0.8F, 0, true).withController(controller);
            controller.getStateHandler().handle(moving);
            String expected = switch (pose) { case TALL -> "crawl_backward"; case TUNNEL -> "crawl_tunnel_backward"; case LOW -> "crawl_low_backward"; };
            var raw = controller.getCurrentRawAnimation();
            if (!raw.equals(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.pale_hound." + expected)))
                throw new AssertionError("Wrong reverse animation for " + pose);
            if (model.getAnimation(hound, "animation.pale_hound." + expected) == null) throw new AssertionError("Reverse asset failed to load");
            if (Math.abs(controller.getAnimationSpeed() - 0.75) > 0.00001) throw new AssertionError("Moving animation slowdown missing");
        }
        controller.getStateHandler().handle(new AnimationState<>(hound, 0, 0, 0, false).withController(controller));
        if (!controller.getCurrentRawAnimation().equals(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.pale_hound.crawl_low_idle")))
            throw new AssertionError("Blocked/idle backpedal must stop the reverse gait");
        hound.discard();
        PaleHound.LOG.info("CLIENT ANIMATION SMOKE PASSED: baked model, all three reverse assets/selections, idle gait stop, 25% playback slowdown and 12-tick posture blending verified");
        client.stop();
    }
}
