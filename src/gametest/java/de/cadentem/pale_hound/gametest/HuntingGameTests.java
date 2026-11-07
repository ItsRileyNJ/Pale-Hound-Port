package de.cadentem.pale_hound.gametest;

import com.mojang.authlib.GameProfile;
import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.entities.goals.PaleHoundHuntGoal;
import de.cadentem.pale_hound.entities.hunting.HoundHuntBrain.Activity;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("pale_hound")
@PrefixGameTestTemplate(false)
public final class HuntingGameTests {
    private static long previousTime;
    @net.minecraft.gametest.framework.BeforeBatch(batch = "sensory_hunting")
    public static void night(net.minecraft.server.level.ServerLevel level) {
        previousTime = level.getDayTime(); level.setDayTime(18000); level.updateSkyBrightness();
    }
    @net.minecraft.gametest.framework.AfterBatch(batch = "sensory_hunting")
    public static void restoreTime(net.minecraft.server.level.ServerLevel level) {
        level.setDayTime(previousTime); level.updateSkyBrightness();
    }
    private static PaleHoundEntity hound(GameTestHelper helper) {
        var hound = ModEntityTypes.PALE_HOUND.get().create(helper.getLevel());
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 64))));
        hound.setYHeadRot(-90); hound.setYRot(-90); hound.setYBodyRot(-90);
        helper.getLevel().addFreshEntity(hound); return hound;
    }
    private static FakePlayer player(GameTestHelper helper, Vec3 position) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "HuntTest"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(position); helper.getLevel().addNewPlayer(player); return player;
    }
    private static void clean(GameTestHelper helper, PaleHoundEntity hound, FakePlayer player) {
        hound.discard(); helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }
    private static void wall(GameTestHelper helper, boolean present) {
        for (int y = 2; y <= 7; ++y) for (int z = 55; z <= 78; ++z)
            helper.setBlock(74, y, z, present ? Blocks.STONE : Blocks.AIR);
    }
    private static void emit(GameTestHelper helper, net.minecraft.core.Holder<GameEvent> event, FakePlayer player) {
        helper.getLevel().gameEvent(event, player.position(), GameEvent.Context.of(player));
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void playerInjuriesAndLandingsRevealOnlyTheirSoundLocation(GameTestHelper helper) throws ReflectiveOperationException {
        var hound = hound(helper);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "InjuryTest")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        player.setGameMode(GameType.SURVIVAL); player.setPos(hound.position().add(0, 0, 20)); helper.getLevel().addNewPlayer(player);
        var protection = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        protection.setAccessible(true); protection.setInt(player, 0);
        try {
            helper.assertTrue(player.hurt(player.damageSources().generic(), 1), "Fixture did not take real health damage");
            var brain = hound.getHuntBrain();
            helper.assertTrue(brain.lastHeardPosition() != null && brain.controlsMovement(), "Real player injury did not trigger investigation");
            hound.tickCount += 5; player.setPos(hound.position().add(0, 0, -20)); emit(helper, GameEvent.HIT_GROUND, player);
            helper.assertTrue(brain.lastHeardPosition().distanceTo(player.position()) < 1, "Landing did not redirect investigation to its sound location");
            helper.assertTrue(hound.getTarget() == null && brain.lastSeenPosition() == null, "Injury/landing granted sight or a live combat target");
            Vec3 heard = brain.lastHeardPosition(); player.setPos(player.position().add(0, 0, -10));
            helper.assertTrue(heard.equals(brain.lastHeardPosition()), "Quiet airborne movement changed the remembered landing");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void chewingAndDrinkingAreAudibleBeforeCompletionButSilentUseIsNot(GameTestHelper helper) {
        var hound = hound(helper); var player = player(helper, hound.position().add(0, 0, 10));
        try {
            var shield = new ItemStack(Items.SHIELD);
            net.neoforged.neoforge.event.EventHooks.onItemUseTick(player, shield, 20);
            var food = new ItemStack(Items.APPLE); player.setItemSlot(EquipmentSlot.MAINHAND, food);
            player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            net.neoforged.neoforge.event.EventHooks.onItemUseTick(player, food, 32);
            var brain = hound.getHuntBrain();
            helper.assertTrue(brain.lastHeardPosition() == null, "Shield use or the silent beginning of a meal made noise");
            player.setShiftKeyDown(true);
            net.neoforged.neoforge.event.EventHooks.onItemUseTick(player, food, 24);
            helper.assertTrue(brain.controlsMovement() && brain.lastHeardPosition() != null, "Audible chewing before completion was ignored, or crouching silenced it");
            player.stopUsingItem(); hound.tickCount += 5; player.setPos(hound.position().add(0, 0, -10));
            var potion = new ItemStack(Items.POTION); player.setItemSlot(EquipmentSlot.MAINHAND, potion);
            player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            net.neoforged.neoforge.event.EventHooks.onItemUseTick(player, potion, 24);
            helper.assertTrue(brain.lastHeardPosition().distanceTo(player.position()) < 1, "Audible sipping did not redirect the investigation");
            player.stopUsingItem(); hound.tickCount += 5; player.setPos(hound.position().add(0, 0, 8));
            food.finishUsingItem(helper.getLevel(), player);
            helper.assertTrue(brain.lastHeardPosition().distanceTo(player.position()) < 1, "The real EAT completion event was ignored");
            hound.tickCount += 5; player.setPos(hound.position().add(0, 0, -8));
            var original = potion.copy(); var result = potion.finishUsingItem(helper.getLevel(), player);
            net.neoforged.neoforge.event.EventHooks.onItemUseFinish(player, original, 0, result);
            helper.assertTrue(brain.lastHeardPosition().distanceTo(player.position()) < 1, "Single-potion completion was ignored after its early bottle return");
            helper.assertTrue(hound.getTarget() == null && brain.lastSeenPosition() == null, "Consumption sounds revealed a live player target");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void audibleEquipmentChangesAttractAttentionWithinTheirShorterRange(GameTestHelper helper) {
        var hound = hound(helper);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "EquipTest")) {
            { firstTick = false; }
        };
        player.setGameMode(GameType.SURVIVAL); player.setPos(hound.position().add(0, 0, 26)); helper.getLevel().addNewPlayer(player);
        try {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            var brain = hound.getHuntBrain();
            helper.assertTrue(brain.lastHeardPosition() == null, "Equipment noise exceeded its twenty-four-block range");
            player.setPos(hound.position().add(0, 0, 14));
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            helper.assertTrue(brain.lastHeardPosition() == null, "A silent hotbar change generated equipment noise");
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            helper.assertTrue(brain.controlsMovement() && brain.lastHeardPosition() != null, "Real armor equip event did not trigger investigation");
            hound.tickCount += 5; player.setPos(hound.position().add(0, 0, -10)); player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            helper.assertTrue(brain.lastHeardPosition().distanceTo(player.position()) < 1, "Removing equipment did not update the heard location");
            helper.assertTrue(hound.getTarget() == null && brain.lastSeenPosition() == null, "Equipment noise granted sight of its player");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void distantSprintingIsHeardButCoverStillMufflesIt(GameTestHelper helper) {
        var hound=hound(helper); var player=player(helper,hound.position().add(40,0,0)); player.setSprinting(true);
        try {
            wall(helper,true); emit(helper,GameEvent.STEP,player);
            helper.assertTrue(hound.getHuntBrain().lastHeardPosition()==null,"Distant sprint ignored cover attenuation");
            wall(helper,false); emit(helper,GameEvent.STEP,player);
            helper.assertTrue(hound.getHuntBrain().controlsMovement(),"Forty-block sprint was not heard with extended hearing");
        } finally {clean(helper,hound,player);}
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void realStepsDistinguishSneakingWalkingAndSprinting(GameTestHelper helper) {
        var hound = hound(helper); hound.setYHeadRot(180);
        var player = player(helper, hound.position().add(0, 0, 20));
        try {
            player.setShiftKeyDown(true); player.setPose(Pose.CROUCHING); emit(helper, GameEvent.STEP, player);
            helper.assertTrue(hound.getHuntBrain().lastHeardPosition() == null, "Careful sneaking generated a step investigation");
            player.setShiftKeyDown(false); player.setPose(Pose.STANDING); emit(helper, GameEvent.STEP, player);
            helper.assertTrue(hound.getHuntBrain().lastHeardPosition() == null, "Ordinary walking was audible twenty blocks away");
            player.setSprinting(true); emit(helper, GameEvent.STEP, player);
            var brain = hound.getHuntBrain();
            helper.assertTrue(brain.activity() == Activity.INVESTIGATING && brain.lastHeardPosition() != null, "Sprinting did not generate a real game-event investigation");
            helper.assertTrue(hound.getTarget() == null && !hound.isAggressive(), "Hearing granted an unseen player target");
            Vec3 remembered = brain.evidencePosition(); player.setPos(player.position().add(30, 0, 0));
            hound.tickCount += 5; brain.tick();
            helper.assertTrue(remembered.equals(brain.evidencePosition()) && hound.getTarget() == null, "Hidden movement updated the sound snapshot");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void coverMufflesDoorsAndUnownedExplosionsCanDistract(GameTestHelper helper) {
        var hound = hound(helper); var player = player(helper, hound.position().add(20, 0, 0));
        try {
            wall(helper, true); emit(helper, GameEvent.BLOCK_OPEN, player);
            helper.assertTrue(hound.getHuntBrain().lastHeardPosition() == null, "Door noise crossed cover at its full open-air range");
            wall(helper, false); emit(helper, GameEvent.CONTAINER_OPEN, player);
            helper.assertTrue(hound.getHuntBrain().controlsMovement(), "Nearby container/door event did not attract attention");
            hound.tickCount += 5;
            Vec3 blast = hound.position().add(0, 0, -30);
            helper.getLevel().gameEvent(GameEvent.EXPLODE, blast, GameEvent.Context.of((Entity)null));
            helper.assertTrue(hound.getHuntBrain().lastHeardPosition().distanceTo(blast) < 1, "Environmental explosion did not create a distraction");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void lostSightPathsToLastKnownPointAndNeverHiddenCoordinates(GameTestHelper helper) {
        var hound = hound(helper); hound.setNoAi(true);
        var player = player(helper, hound.position().add(20, 0, 0));
        try {
            helper.assertTrue(hound.tryStartSurfaceStalking(player), "Could not begin visible stalking");
            Vec3 seen = player.position(); wall(helper, true); player.setPos(player.position().add(0, 0, 6));
            var brain = hound.getHuntBrain(); brain.tick();
            helper.assertTrue(brain.activity() == Activity.SEARCHING && seen.equals(brain.evidencePosition()), "Loss of sight did not preserve the observed location");
            helper.assertTrue(!hound.isSurfaceStalking() && hound.getTarget() == null && !hound.canAttack(player), "Hidden player remains combat-targeted");
            hound.setTarget(player); helper.assertTrue(hound.getTarget() == null, "Legacy target setter reacquired through a wall");
            var goal = new PaleHoundHuntGoal(hound); goal.start(); goal.tick();
            var path = hound.getNavigation().getPath();
            helper.assertTrue(path != null && path.getTarget().equals(BlockPos.containing(seen)), "Investigation path used hidden live position instead of memory");
            player.setPos(player.position().add(20, 0, 8)); hound.tickCount += 5; brain.tick();
            helper.assertTrue(seen.equals(brain.evidencePosition()) && hound.getTarget() == null, "Moving unseen dragged the investigation target");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void searchingHunterReacquiresOnlyAfterSightReturns(GameTestHelper helper) {
        var hound = hound(helper); hound.setNoAi(true); var player = player(helper, hound.position().add(20, 0, 0));
        try {
            hound.beginPlayerPursuit(player); hound.getHuntBrain().tick(); wall(helper, true); hound.getHuntBrain().tick();
            helper.assertTrue(hound.getHuntBrain().activity() == Activity.SEARCHING && hound.getTarget() == null, "A lost chase did not become a search");
            helper.assertTrue(hound.isAggressive() && !hound.canAttack(player), "An angry trail should preserve pursuit posture without attacking an unseen player");
            wall(helper, false); hound.tickCount += 5; hound.getHuntBrain().tick();
            helper.assertTrue(hound.getTarget() == player && hound.isAggressive() && hound.isSurfaceStalkAttack(), "Sight returning did not resume the earned pursuit");
            helper.assertTrue(hound.getHuntBrain().activity() == Activity.NONE, "Search kept controlling movement after reacquisition");
            player.discard(); hound.getHuntBrain().tick();
            helper.assertTrue(hound.getTarget() == null && hound.getHuntBrain().activity() == Activity.SEARCHING,
                "Player removal left a live ghost target instead of a finite search");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void quietSearchEndsAndLoudNewEvidenceCanReopenIt(GameTestHelper helper) {
        var hound = hound(helper); hound.setNoAi(true); hound.setYHeadRot(180);
        var player = player(helper, hound.position().add(0, 0, 20));
        try {
            var brain = hound.getHuntBrain(); helper.assertTrue(brain.hear(player, player.position(), 28, 24), "Sprinting evidence not heard");
            for (int i = 0; i < ServerConfig.HUNT_SEARCH_SECONDS.get() * 20 + 5; ++i) { hound.tickCount++; brain.tick(); }
            helper.assertTrue(brain.activity() == Activity.RECOVERING && hound.getTarget() == null, "Quiet hiding did not end the search");
            helper.assertTrue(!hound.tryStartSurfaceStalking(player), "Recovery instantly restarted stalking");
            helper.assertTrue(!brain.hear(player, player.position(), 28, 10), "A quiet disturbance defeated the recovery interval");
            hound.tickCount += 5;
            helper.assertTrue(brain.hear(player, player.position(), 28, 24) && brain.controlsMovement(), "New loud evidence could not restart investigation");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void repeatedDisturbancesRaiseSuspicionWithoutGrantingSight(GameTestHelper helper) {
        var hound = hound(helper); hound.setNoAi(true); hound.setYHeadRot(180);
        var player = player(helper, hound.position().add(0, 0, 20));
        try {
            var brain = hound.getHuntBrain();
            for (int i = 0; i < 8; ++i) { hound.tickCount += 5; brain.hear(player, player.position(), 28, 22); }
            helper.assertTrue(brain.suspicion() == 100 && brain.activity() == Activity.SEARCHING, "Repeated hiding-place noise did not increase interest with a bounded suspicion score");
            helper.assertTrue(hound.getTarget() == null && brain.lastSeenPosition() == null, "Repeated noise became omniscient visual detection");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void heldLightsExtendVisionButCannotRevealThroughWalls(GameTestHelper helper) {
        var hound = hound(helper); hound.setNoAi(true);
        var player = player(helper, hound.position().add(0, 0, -80));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 100))));
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 20))));
        hound.setYHeadRot(0);
        try {
            helper.assertTrue(!hound.canSeePlayer(player), "Unlit player was visible beyond base range");
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.LANTERN));
            helper.assertTrue(hound.canSeePlayer(player), "Offhand lantern did not extend detection range");
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TORCH));
            helper.assertTrue(hound.canSeePlayer(player), "Held torch did not extend detection range");
            for (int x = 60; x <= 68; ++x) for (int y = 2; y <= 7; ++y) helper.setBlock(x, y, 60, Blocks.STONE);
            helper.assertTrue(!hound.canSeePlayer(player), "Held light revealed a player through solid cover");
            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(!hound.canSeePlayer(player), "Held light bypassed Creative protection");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, timeoutTicks = 80, batch = "sensory_hunting")
    public static void standingInBlockLightExtendsDistantVisibility(GameTestHelper helper) {
        var hound = hound(helper); hound.setNoAi(true); hound.setYHeadRot(0);
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 20))));
        var player = player(helper, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 100))));
        helper.assertTrue(!hound.canSeePlayer(player), "Unlit distant player should be hard to see");
        helper.setBlock(65, 2, 100, Blocks.SEA_LANTERN);
        helper.succeedWhen(() -> {
            helper.assertTrue(hound.canSeePlayer(player), "Bright surroundings did not extend detection: block light="
                + helper.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, player.blockPosition())
                + " range=" + hound.getHuntBrain().visionRange(player, false) + " LOS=" + hound.hasLineOfSight(player));
            clean(helper, hound, player);
        });
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, timeoutTicks = 380, batch = "sensory_hunting")
    public static void heardEvidenceProducesRealMovementWhilePlayerHides(GameTestHelper helper) {
        var hound = hound(helper); Vec3 start = hound.position(); hound.setYHeadRot(180);
        var player = player(helper, start.add(20, 0, 0)); player.setSprinting(true); emit(helper, GameEvent.STEP, player);
        helper.assertTrue(hound.getHuntBrain().controlsMovement(), "No investigation from a sprint");
        player.setSprinting(false); player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(100, 2, 100))));
        for (int x = 98; x <= 102; ++x) for (int y = 2; y <= 7; ++y) for (int z = 98; z <= 102; ++z)
            if (x == 98 || x == 102 || z == 98 || z == 102 || y == 7) helper.setBlock(x, y, z, Blocks.STONE);
        helper.runAfterDelay(300, () -> {
            try {
                helper.assertTrue(!hound.isRemoved() && hound.position().distanceTo(start) > 6, "Hearing changed state but never moved the Hound");
                helper.assertTrue(hound.getTarget() == null && hound.getHuntBrain().lastSeenPosition() == null, "Search acquired the player hidden inside solid cover");
            } finally { clean(helper, hound, player); }
            helper.succeed();
        });
    }
    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void unlitSneakingAndTheVisionConePermitStealth(GameTestHelper helper) {
        var hound = hound(helper); hound.setNoAi(true); hound.setYHeadRot(0);
        var player = player(helper, hound.position().add(0, 0, 30));
        try {
            helper.assertTrue(hound.canSeePlayer(player), "Standing player ahead should be visible");
            player.setPose(Pose.CROUCHING); player.setShiftKeyDown(true);
            helper.assertTrue(!hound.canSeePlayer(player), "Quiet crouching did not reduce discovery range in darkness");
            helper.assertTrue(hound.getHuntBrain().hear(player,player.position(),36,24),"Could not create an audible crouching-player disturbance");
            helper.assertTrue(hound.canSeePlayer(player),"Recent noise did not remove the dark-crouching discovery advantage");
            hound.tickCount += 41;
            helper.assertTrue(!hound.canSeePlayer(player),"Quiet crouching did not regain its discovery advantage after the sound cue aged");
            player.setPose(Pose.STANDING); player.setShiftKeyDown(false); player.setPos(hound.position().add(0, 0, -30));
            helper.assertTrue(!hound.canSeePlayer(player), "Hound discovered a player behind his vision cone");
            player.setPos(hound.position().add(0, 0, -10));
            helper.assertTrue(hound.canSeePlayer(player), "Very close contact should remain detectable behind him");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "sensory_hunting")
    public static void exhaustedChaseDisengagesInsteadOfVanishing(GameTestHelper helper) {
        var hound = hound(helper); hound.setNoAi(true);
        var player = player(helper, hound.position().add(20, 0, 0));
        try {
            hound.beginPlayerPursuit(player); hound.targetIsFacingMe = false;
            var chase = new de.cadentem.pale_hound.entities.goals.PaleHoundChaseGoal(hound, true); chase.start();
            for (int i = 0; i <= de.cadentem.pale_hound.util.Utils.presenceTicks(ServerConfig.TIME_UNTIL_LEAVE_CHASE.get()); ++i) chase.tick();
            helper.assertTrue(!hound.isRemoved() && hound.getTarget() == null && hound.getHuntBrain().activity() == Activity.RECOVERING,
                "Looking away at the chase deadline removed the Hound instead of disengaging");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, timeoutTicks = 60, batch = "sensory_hunting")
    public static void experiencedHoundWithdrawsFromHeldLavaWithoutStareInterference(GameTestHelper helper) {
        var hound = hound(helper); var start = hound.position();
        var knowledge = new net.minecraft.nbt.CompoundTag(); knowledge.putInt("LAVA", 2); hound.getHazardLearning().load(knowledge);
        var player = player(helper, start.add(14, 0, 0)); player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.LAVA_BUCKET));
        hound.currentRoll = de.cadentem.pale_hound.entities.goals.Roll.STARE; hound.setTarget(player);
        helper.runAfterDelay(20, () -> {
            try {
                helper.assertTrue(!hound.isRemoved() && hound.distanceTo(player) > 17, "Learned caution did not create actual movement away from the held hazard");
            } finally { clean(helper, hound, player); }
            helper.succeed();
        });
    }

}
