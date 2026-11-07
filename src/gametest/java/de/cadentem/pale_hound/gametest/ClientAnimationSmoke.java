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
            if (!raw.getAnimationStages().getLast().animationName().equals("animation.pale_hound." + expected))
                throw new AssertionError("Wrong reverse animation for " + pose);
            if (model.getAnimation(hound, "animation.pale_hound." + expected) == null) throw new AssertionError("Reverse asset failed to load");
            if (Math.abs(controller.getAnimationSpeed() - 0.75) > 0.00001) throw new AssertionError("Moving animation slowdown missing");
        }
        controller.getStateHandler().handle(new AnimationState<>(hound, 0, 0, 0, false).withController(controller));
        if (!controller.getCurrentRawAnimation().getAnimationStages().getLast().animationName().equals("animation.pale_hound.crawl_low_idle"))
            throw new AssertionError("Blocked/idle backpedal must stop the reverse gait");
        hound.setBackingAway(false);
        hound.getEntityData().set(PaleHoundEntity.FLEEING_ACCESSOR, true);
        hound.setDeltaMovement(new Vec3(0.6, 0, 0));
        controller.getStateHandler().handle(new AnimationState<>(hound, 1, 1, 0, true).withController(controller));
        if (Math.abs(controller.getAnimationSpeed() - 6.0) > 0.00001)
            throw new AssertionError("Crawling escape must also match its fast physical movement");
        hound.setCrawling(false); hound.setBackingAway(false);
        hound.getEntityData().set(PaleHoundEntity.FLEEING_ACCESSOR, true);
        hound.setAggressive(true); // Escape pose must win over any stale chase flag.
        hound.setDeltaMovement(new Vec3(0.6, 0, 0));
        controller.getStateHandler().handle(new AnimationState<>(hound, 1, 1, 0, true).withController(controller));
        if (!controller.getCurrentRawAnimation().getAnimationStages().getLast().animationName().equals("animation.pale_hound.flee")
            || Math.abs(controller.getAnimationSpeed() - 3.0) > 0.00001 || transition.getDouble(controller) != 3)
            throw new AssertionError("Fast escape must have a responsive, movement-matched running gait");
        hound.setDeltaMovement(new Vec3(0.1, 0, 0));
        controller.getStateHandler().handle(new AnimationState<>(hound, 1, 1, 0, true).withController(controller));
        if (controller.getAnimationSpeed() != 1.0) throw new AssertionError("Escape gait must adjust to slower movement");
        for (String from : new String[]{"walk","run","crouch","stalk","tunnel","inspect"})
            for (String to : new String[]{"walk","run","crouch","stalk","tunnel","inspect"})
                if (!from.equals(to) && model.getAnimation(hound,"animation.pale_hound.transition_"+from+"_to_"+to)==null)
                    throw new AssertionError("Missing posture transition " + from + " to " + to);
        hound.getEntityData().set(PaleHoundEntity.FLEEING_ACCESSOR,false); hound.setAggressive(false);
        var breathing=hound.getAnimatableInstanceCache().<PaleHoundEntity>getManagerForId(hound.getId()).getAnimationControllers().get("breathing");
        lastModel.set(breathing,model);
        var idleBreath=new AnimationState<>(hound,0,0,0,false).withController(breathing);
        if(breathing.getStateHandler().handle(idleBreath)!=software.bernie.geckolib.animation.PlayState.CONTINUE)
            throw new AssertionError("Idle breathing did not start");
        if(breathing.getStateHandler().handle(new AnimationState<>(hound,1,1,0,true).withController(breathing))!=software.bernie.geckolib.animation.PlayState.STOP)
            throw new AssertionError("Breathing did not fade out for movement");
        hound.discard();
        var inspection = new PaleHoundEntity(ModEntityTypes.PALE_HOUND.get(), client.level);
        inspection.setPos(client.player.position().add(0, 10, 0));
        inspection.setInspectingGround(true);
        if (!inspection.isCrawling() || inspection.getCrawlPose() != PaleHoundEntity.CrawlPose.LOW)
            throw new AssertionError("Ground inspection did not request the lowest pose in open air");
        var inspectingController = inspection.getAnimatableInstanceCache().<PaleHoundEntity>getManagerForId(inspection.getId()).getAnimationControllers().get("controller");
        lastModel.set(inspectingController, model);
        inspectingController.getStateHandler().handle(new AnimationState<>(inspection, 0, 0, 0, false).withController(inspectingController));
        if (!inspectingController.getCurrentRawAnimation().getAnimationStages().getLast().animationName().equals("animation.pale_hound.crawl_low_idle")
            || transition.getDouble(inspectingController) != 12)
            throw new AssertionError("Ground inspection did not play low idle with the existing slow posture blend");
        inspection.setInspectingGround(false); inspection.discard();
        PaleHound.LOG.info("CLIENT ANIMATION SMOKE PASSED: baked model, reverse assets/selections, idle gait stop, 25% playback slowdown, 12-tick posture blending, movement-matched fleeing lowest ground-inspection idle, 30 posture transitions and idle breathing verified");
        client.stop();
    }
}
