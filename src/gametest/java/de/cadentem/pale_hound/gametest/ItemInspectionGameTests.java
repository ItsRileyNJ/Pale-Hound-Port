package de.cadentem.pale_hound.gametest;

import com.mojang.authlib.GameProfile;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.entities.goals.PaleHoundHuntGoal;
import de.cadentem.pale_hound.entities.goals.PaleHoundInspectItemGoal;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
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
public final class ItemInspectionGameTests {
    private static PaleHoundEntity hound(GameTestHelper helper) {
        var hound = ModEntityTypes.PALE_HOUND.get().create(helper.getLevel());
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 64))));
        hound.setOnGround(true); hound.getRandom().setSeed(12345);
        helper.getLevel().addFreshEntity(hound); return hound;
    }
    private static ItemEntity item(GameTestHelper helper, Vec3 position) {
        var item = new ItemEntity(helper.getLevel(), position.x, position.y, position.z, new ItemStack(Items.APPLE, 3));
        item.setDeltaMovement(Vec3.ZERO); item.setOnGround(true); helper.getLevel().addFreshEntity(item); return item;
    }
    private static FakePlayer player(GameTestHelper helper, Vec3 position) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "InspectTest"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(position); helper.getLevel().addNewPlayer(player); return player;
    }
    private static PaleHoundInspectItemGoal prepare(GameTestHelper helper, PaleHoundEntity hound) {
        var goal = hound.getItemInspectionGoal();
        for (int i = 0; i < 40; ++i) {
            hound.tickCount += 250;
            if (goal.canUse()) return goal;
        }
        helper.fail("No inspection was selected for a visible reachable item"); return goal;
    }
    private static void remove(GameTestHelper helper, FakePlayer player) {
        helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, timeoutTicks = 220, batch = "item_inspection")
    public static void realApproachLowersBrieflyThenResumesWithoutTakingItems(GameTestHelper helper) {
        var hound = hound(helper); Vec3 start = hound.position(); var item = item(helper, start.add(7, 0, 0));
        prepare(helper, hound); boolean[] looked = {false}; int[] beganLooking = {-1};
        Vec3[] departedFrom = {null};
        helper.onEachTick(() -> {
            if (!hound.isInspectingGround()) return;
            looked[0] = true;
            if (beganLooking[0] < 0) beganLooking[0] = hound.tickCount;
            helper.assertTrue(hound.isCrawling() && hound.getCrawlPose() == PaleHoundEntity.CrawlPose.LOW,
                "Ground inspection did not use the lowest pose");
            helper.assertTrue(hound.distanceToSqr(item) < 9, "Hound inspected an item from far away");
            helper.assertTrue(hound.tickCount - beganLooking[0] <= 65, "Item inspection became a long idle stop");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(looked[0] && !hound.isInspectingItem(), "Inspection has not completed");
            helper.assertTrue(hound.position().distanceTo(start) > 3, "Inspection state changed without real approach movement");
            helper.assertTrue(!item.isRemoved() && item.getItem().getCount() == 3, "Inspection consumed or removed the dropped stack");
            helper.assertTrue(!hound.isInspectingGround(), "Low inspection pose remained latched after completion");
            if (departedFrom[0] == null) departedFrom[0] = hound.position();
            helper.assertTrue(hound.position().distanceToSqr(departedFrom[0]) > 1, "Inspection completed but roaming did not physically resume");
            hound.discard(); item.discard();
        });
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "item_inspection")
    public static void acceptedSoundCancelsInspectionAndImmediatelyBecomesEvidence(GameTestHelper helper) {
        var hound = hound(helper); var item = item(helper, hound.position().add(2, 0, 0));
        var player = player(helper, hound.position().add(0, 0, 40));
        try {
            var goal = prepare(helper, hound); goal.start(); goal.tick();
            helper.assertTrue(hound.isInspectingGround(), "Fixture did not settle into low inspection");
            player.setPos(hound.position().add(0, 0, 20)); player.setSprinting(true);
            helper.getLevel().gameEvent(GameEvent.STEP, player.position(), GameEvent.Context.of(player));
            helper.assertTrue(!hound.isInspectingItem() && !hound.isInspectingGround(), "Heard sprint did not immediately cancel item attention and low pose");
            helper.assertTrue(hound.getHuntBrain().controlsMovement() && hound.getHuntBrain().lastHeardPosition() != null,
                "Sound cancellation did not hand control to evidence hunting");
            helper.assertTrue(!goal.canUse() && hound.getTarget() == null, "Inspection instantly restarted or granted a hidden combat target");
        } finally { hound.discard(); item.discard(); remove(helper, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "item_inspection")
    public static void passiveCreaturesCannotInterruptStalkingInspectionButPlayerNoiseCan(GameTestHelper helper) {
        var hound = hound(helper); var item = item(helper, hound.position().add(0, 0, 2));
        var player = player(helper, hound.position().add(40, 0, 0));
        hound.setYHeadRot(-90);
        try {
            helper.assertTrue(hound.tryStartSurfaceStalking(player), "Could not start calm surface stalking");
            var goal = prepare(helper, hound); goal.start(); goal.tick();
            for (var type : java.util.List.of(EntityType.BAT, EntityType.SHEEP, EntityType.WOLF, EntityType.VILLAGER)) {
                var animal = type.create(helper.getLevel()); animal.setPos(hound.position().add(0, 0, 4));
                for (var event : java.util.List.of(GameEvent.STEP, GameEvent.SPLASH, GameEvent.HIT_GROUND, GameEvent.ENTITY_DAMAGE,
                    GameEvent.BLOCK_OPEN, GameEvent.BLOCK_ACTIVATE, GameEvent.PROJECTILE_LAND))
                    helper.getLevel().gameEvent(event, animal.position(), GameEvent.Context.of(animal));
                animal.discard();
            }
            helper.assertTrue(hound.isInspectingItem() && hound.isSurfaceStalking() && hound.getHuntBrain().lastHeardPosition() == null,
                "Passive creature noise interrupted stalking/inspection or supplied hearing evidence");
            helper.getLevel().gameEvent(GameEvent.EXPLODE, player.position(), GameEvent.Context.of(player));
            helper.assertTrue(!hound.isInspectingItem() && hound.isSurfaceStalking() && hound.getHuntBrain().lastHeardPosition() != null,
                "A relevant sound did not cancel inspection when the watched player was already visible");
            for (int y = 2; y <= 8; ++y) for (int z = 58; z <= 70; ++z) helper.setBlock(74, y, z, Blocks.STONE);
            hound.getHuntBrain().tick();
            helper.assertTrue(hound.getHuntBrain().controlsMovement(), "Lost stalking target did not enter evidence hunting");
            Vec3 evidence = hound.getHuntBrain().evidencePosition(); int revision = hound.getHuntBrain().revision();
            var bat = EntityType.BAT.create(helper.getLevel()); bat.setPos(hound.position().add(0, 0, 3));
            helper.getLevel().gameEvent(GameEvent.BLOCK_ACTIVATE, bat.position(), GameEvent.Context.of(bat)); bat.discard();
            helper.assertTrue(evidence.equals(hound.getHuntBrain().evidencePosition()) && revision == hound.getHuntBrain().revision(),
                "Bat redirected the evidence hunt");
        } finally { hound.discard(); item.discard(); remove(helper, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "item_inspection")
    public static void hiddenItemsAreRejectedAndLostSightCancelsWithoutWallStaring(GameTestHelper helper) {
        var hound = hound(helper); var item = item(helper, hound.position().add(8, 0, 0));
        var goal = hound.getItemInspectionGoal();
        try {
            for (int y = 2; y <= 8; ++y) for (int z = 58; z <= 70; ++z) helper.setBlock(68, y, z, Blocks.STONE);
            for (int i = 0; i < 20; ++i) {
                hound.tickCount += 250; helper.assertTrue(!goal.canUse(), "Inspected a dropped item through a solid wall");
            }
            for (int y = 2; y <= 8; ++y) for (int z = 58; z <= 70; ++z) helper.setBlock(68, y, z, Blocks.AIR);
            prepare(helper, hound); goal.start();
            for (int y = 2; y <= 8; ++y) for (int z = 58; z <= 70; ++z) helper.setBlock(68, y, z, Blocks.STONE);
            goal.tick();
            helper.assertTrue(!hound.isInspectingItem() && !hound.isInspectingGround() && hound.getNavigation().isDone(),
                "Lost item visibility left movement or wall-staring active");
        } finally { hound.discard(); item.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "item_inspection")
    public static void noProgressAbandonsInspectionWithinOneSecondAndDoesNotRetryImmediately(GameTestHelper helper) {
        var hound = hound(helper); var item = item(helper, hound.position().add(8, 0, 0));
        try {
            var goal = prepare(helper, hound); goal.start(); goal.tick();
            hound.tickCount += 21; goal.tick();
            helper.assertTrue(!hound.isInspectingItem() && !hound.isInspectingGround() && hound.getNavigation().isDone(),
                "A stationary approach kept an inspection active instead of abandoning its route");
            helper.assertTrue(!goal.canUse(), "Unreachable item was immediately selected again");
        } finally { hound.discard(); item.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "item_inspection")
    public static void approachingPlayerAndRemovedItemBothTakePriorityOverCuriosity(GameTestHelper helper) {
        var hound = hound(helper); var item = item(helper, hound.position().add(2, 0, 0));
        var player = player(helper, hound.position().add(40, 0, 0));
        try {
            var goal = prepare(helper, hound); goal.start(); goal.tick();
            player.setPos(hound.position().add(15, 0, 0)); goal.tick();
            helper.assertTrue(!hound.isInspectingItem() && !hound.isInspectingGround(), "Nearby player failed to interrupt curiosity");
            player.setPos(hound.position().add(40, 0, 0)); prepare(helper, hound); goal.start(); goal.tick();
            item.discard(); goal.tick();
            helper.assertTrue(!hound.isInspectingItem() && !hound.isInspectingGround(), "Removed item left the inspection latched");
        } finally { hound.discard(); item.discard(); remove(helper, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "item_inspection")
    public static void arrivingAtGroundEvidenceUsesLowPoseAndFailedRouteDoesNotPretendToArrive(GameTestHelper helper) {
        var hound = hound(helper); var player = player(helper, hound.position().add(8, 0, 0));
        try {
            helper.assertTrue(hound.getHuntBrain().hear(player, player.position(), 24, 24), "Could not create sound evidence");
            var goal = new PaleHoundHuntGoal(hound); goal.start(); goal.tick();
            hound.getNavigation().stop(); goal.tick();
            helper.assertTrue(!hound.isInspectingGround() && hound.getHuntBrain().legs() == 0,
                "Failed path was marked as an arrival and became a ground-staring pause");
            hound.tickCount += 5; hound.getHuntBrain().hear(player, player.position(), 24, 24); goal.tick();
            hound.setPos(hound.getHuntBrain().evidencePosition()); goal.tick();
            helper.assertTrue(hound.isInspectingGround() && hound.getCrawlPose() == PaleHoundEntity.CrawlPose.LOW,
                "Actual arrival at a ground evidence point did not lower his pose");
            goal.stop(); helper.assertTrue(!hound.isInspectingGround(), "Evidence goal stop left low posture active");
        } finally { hound.discard(); remove(helper, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "item_inspection")
    public static void ordinaryDiagonalPathsSkipClearWaypointsWithoutCuttingWalls(GameTestHelper helper) {
        var hound = hound(helper);
        try {
            var path = hound.getNavigation().createPath(BlockPos.containing(hound.position().add(12, 0, 12)), 0);
            helper.assertTrue(path != null && path.canReach(), "Could not create diagonal movement path");
            hound.getNavigation().moveTo(path, 0.35); hound.getNavigation().tick();
            helper.assertTrue(path.getNextNodeIndex() >= path.getNodeCount() - 2, "Ordinary navigation retained the open-ground left/right staircase");
            helper.setBlock(70, 2, 70, Blocks.STONE);
            helper.assertTrue(!de.cadentem.pale_hound.entities.navigation.HoundRoutes.canWalkStraight(hound, hound.position().add(12, 0, 12)),
                "Fluid path smoothing cut through a solid wall");
        } finally { hound.discard(); }
        helper.succeed();
    }
}
