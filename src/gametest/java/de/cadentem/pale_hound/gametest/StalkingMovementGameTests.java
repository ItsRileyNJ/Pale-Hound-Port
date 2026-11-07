package de.cadentem.pale_hound.gametest;

import com.mojang.authlib.GameProfile;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.entities.navigation.HoundEscapeVisibility;
import de.cadentem.pale_hound.entities.navigation.HoundRoutes;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("pale_hound")
@PrefixGameTestTemplate(false)
public final class StalkingMovementGameTests {
    private static PaleHoundEntity hound(GameTestHelper helper, int x, int z) {
        var hound = ModEntityTypes.PALE_HOUND.get().create(helper.getLevel());
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 2, z))));
        helper.getLevel().addFreshEntity(hound);
        return hound;
    }
    private static FakePlayer player(GameTestHelper helper, Vec3 position) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StalkMotionTest"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(position);
        helper.getLevel().addNewPlayer(player);
        return player;
    }
    private static void lookAt(FakePlayer player, PaleHoundEntity hound) {
        Vec3 direction = hound.getEyePosition().subtract(player.getEyePosition());
        player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        player.setYHeadRot(player.getYRot());
        player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
    }
    private static void clean(GameTestHelper helper, PaleHoundEntity hound, FakePlayer player) {
        hound.discard(); helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, timeoutTicks = 140, batch = "stalking_motion")
    public static void closePlayerGetsBackwardCrawlWithoutTurningOrDespawn(GameTestHelper helper) {
        var hound = hound(helper, 32, 32);
        Vec3 start = hound.position();
        var player = player(helper, start.add(15.5, 0, 0));
        hound.setYRot(-90); hound.setYBodyRot(-90); hound.setYHeadRot(-90);
        lookAt(player, hound);
        helper.assertTrue(hound.tryStartSurfaceStalking(player), "Could not start close stalking");
        helper.onEachTick(() -> {
            if (hound.isRemoved()) return;
            helper.assertTrue(!hound.isRemoved() && !hound.isSurfaceStalkRetreating(), "Close gaze caused a full escape: removed=" + hound.isRemoved() + " retreat=" + hound.isSurfaceStalkRetreating() + " position=" + hound.position() + " distance=" + hound.distanceTo(player));
            helper.assertTrue(Math.abs(Mth.wrapDegrees(hound.getYRot() + 90)) < 8, "Backpedalling turned away from player");
            helper.assertTrue(!hound.canAttack(player), "Backpedalling enabled attacks");
        });
        helper.runAfterDelay(100, () -> {
            try {
                helper.assertTrue(!hound.isRemoved() && hound.isBackingAway() && hound.isCrawling(), "Backward crawling state missing");
                helper.assertTrue(hound.getX() < start.x - 2 && Math.abs(hound.getZ() - start.z) < 0.8,
                    "Did not physically walk backward: " + hound.position());
                helper.assertTrue(hound.getSpeedModifier() < 0.35, "Backwards movement should be slower than following");
            } finally { clean(helper, hound, player); }
            helper.succeed();
        });
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, timeoutTicks = 90, batch = "stalking_motion")
    public static void diagonalEscapeDoesNotAlternateCardinalHeadings(GameTestHelper helper) {
        var hound = hound(helper, 48, 48);
        Vec3 start = hound.position();
        var player = player(helper, start.add(-9.5, 0, -9.5));
        hound.setNoAi(true);
        helper.assertTrue(hound.tryStartSurfaceStalking(player), "Could not start diagonal stalking");
        for (int i = 0; i < 160; ++i) hound.tick();
        lookAt(player, hound); for (int i = 0; i < 20; ++i) hound.tick();
        helper.assertTrue(hound.isSurfaceStalkRetreating(), "Gaze did not trigger escape after arrival grace");
        hound.setNoAi(false);
        float[] lastYaw = {hound.getYRot()}; int[] elapsed = {0};
        helper.onEachTick(() -> {
            if (++elapsed[0] > 6) helper.assertTrue(Math.abs(Mth.wrapDegrees(hound.getYRot() - lastYaw[0])) < 15,
                "Diagonal escape wiggled between headings: " + lastYaw[0] + " -> " + hound.getYRot());
            lastYaw[0] = hound.getYRot();
            helper.assertTrue(Math.abs((hound.getX() - start.x) - (hound.getZ() - start.z)) < 1,
                "Open-ground escape deviated from its straight diagonal");
        });
        helper.runAfterDelay(45, () -> {
            try {
                helper.assertTrue(!hound.isRemoved() && hound.distanceToSqr(start) > 8 * 8, "Escape did not gain distance");
            } finally { clean(helper, hound, player); }
            helper.succeed();
        });
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "stalking_motion")
    public static void escapeRequiresHiddenBodyAndHeadForOneSecond(GameTestHelper helper) {
        var hound = hound(helper, 32, 32); hound.setNoAi(true); hound.setCrawling(true);
        var player = player(helper, hound.position().add(40, 0, 0));
        var visibility = new HoundEscapeVisibility();
        try {
            // Looking away is deliberately insufficient: a visible body must keep running.
            for (int i = 0; i < 120; ++i) helper.assertTrue(!visibility.canDisappear(hound, 60), "Visible Hound vanished on a timer");
            var feet = hound.blockPosition();
            for (int z = -2; z <= 2; ++z) helper.getLevel().setBlockAndUpdate(feet.offset(2, 0, z), Blocks.STONE.defaultBlockState());
            helper.assertTrue(!player.hasLineOfSight(hound), "Low wall should hide the small physical crawl eye");
            helper.assertTrue(HoundEscapeVisibility.isVisible(hound), "Exposed tall crawl head must still count as visible");
            for (int x = -2; x <= 2; ++x) for (int y = 0; y <= 5; ++y) for (int z = -2; z <= 2; ++z)
                if (Math.abs(x) == 2 || Math.abs(z) == 2 || y == 5)
                    helper.getLevel().setBlockAndUpdate(feet.offset(x, y, z), Blocks.STONE.defaultBlockState());
            helper.assertTrue(!HoundEscapeVisibility.isVisible(hound), "Enclosed Hound should be hidden from every player");
            for (int i = 0; i < 19; ++i) helper.assertTrue(!visibility.canDisappear(hound, 60), "Brief occlusion removed Hound");
            helper.assertTrue(visibility.canDisappear(hound, 60), "Sustained occlusion should finish escape");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "stalking_motion")
    public static void watchFacesPlayerAndArrivalGazeHasGrace(GameTestHelper helper) {
        var hound = hound(helper, 16, 32); hound.setNoAi(true);
        var player = player(helper, hound.position().add(40, 0, 0));
        try {
            helper.assertTrue(hound.tryStartSurfaceStalking(player), "Could not start watching");
            var goal = new de.cadentem.pale_hound.entities.goals.PaleHoundSurfaceStalkGoal(hound);
            goal.start(); for (int i = 0; i < 30; ++i) goal.tick();
            helper.assertTrue(hound.getNavigation().isDone() && Math.abs(Mth.wrapDegrees(hound.getYRot() + 90)) < 1,
                "Watching should hold position and face the player");
            lookAt(player, hound);
            for (int i = 0; i < 159; ++i) { hound.tick(); helper.assertTrue(!hound.isSurfaceStalkRetreating(), "Arrival gaze escaped immediately"); }
            for (int i = 0; i < 80; ++i) hound.tick();
            helper.assertTrue(!hound.isSurfaceStalkRetreating(), "Distant gaze must allow the player to approach");
            player.setPos(hound.position().add(13, 0, 0)); lookAt(player, hound);
            for (int i = 0; i < 19; ++i) {
                hound.tick(); helper.assertTrue(!hound.isSurfaceStalkRetreating(), "Close gaze must leave time to cross into attack range");
            }
            hound.tick(); helper.assertTrue(hound.isSurfaceStalkRetreating(), "Close gaze should run after the reaction pause");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "stalking_motion")
    public static void straightEscapeShortcutRejectsWallsAndHoles(GameTestHelper helper) {
        var hound = hound(helper, 20, 32); hound.setNoAi(true);
        Vec3 destination = hound.position().add(10, 0, 0);
        try {
            helper.assertTrue(HoundRoutes.canWalkStraight(hound, destination), "Open ground should allow a straight escape");
            helper.setBlock(25, 2, 32, Blocks.STONE);
            helper.assertTrue(!HoundRoutes.canWalkStraight(hound, destination), "Straight shortcut crossed a wall");
            helper.setBlock(25, 2, 32, Blocks.AIR); helper.setBlock(25, 1, 32, Blocks.AIR);
            helper.assertTrue(!HoundRoutes.canWalkStraight(hound, destination), "Straight shortcut crossed a hole");
        } finally { hound.discard(); }
        helper.succeed();
    }
    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "stalking_motion")
    public static void twelveBlockAttackOverridesPendingGazeEscape(GameTestHelper helper) {
        var hound = hound(helper, 64, 64); hound.setNoAi(true);
        var player = player(helper, hound.position().add(40, 0, 0));
        try {
            helper.assertTrue(hound.tryStartSurfaceStalking(player), "Could not start stalking");
            for (int i = 0; i < 160; ++i) hound.tick();
            player.setPos(hound.position().add(13, 0, 0)); lookAt(player, hound);
            for (int i = 0; i < 10; ++i) hound.tick();
            helper.assertTrue(hound.isSurfaceStalking() && !hound.isSurfaceStalkRetreating(), "No approach window before escape");
            player.setPos(hound.position().add(12, 0, 0)); player.setYRot(-90); player.setYHeadRot(-90); player.setXRot(0);
            hound.tick();
            helper.assertTrue(!hound.isSurfaceStalking() && hound.isSurfaceStalkAttack(), "Exact 12-block approach did not commit to attacking");
            helper.assertTrue(hound.getTarget() == player && hound.isAggressive() && !hound.isFleeing && !hound.isBackingAway(), "Attack transition retained passive or fleeing state");
            helper.assertTrue(hound.currentRoll == de.cadentem.pale_hound.entities.goals.Roll.CHASE && hound.canAttack(player), "Attack guard still suppresses the committed chase");
            helper.assertTrue(!hound.targetIsFacingMe, "Fixture should face away");
            var chase = new de.cadentem.pale_hound.entities.goals.PaleHoundChaseGoal(hound, true);
            helper.assertTrue(chase.canUse(), "Committed attack incorrectly requires the player to look at him");
        } finally { clean(helper, hound, player); }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, timeoutTicks = 170, batch = "stalking_motion")
    public static void crowdedSurfaceStalkerPhysicallyChasesAndHits(GameTestHelper helper) throws ReflectiveOperationException {
        var hound = hound(helper, 64, 64);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CrowdedStalkTest")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource damage) { return false; }
        };
        player.setGameMode(GameType.SURVIVAL); player.setPos(hound.position().add(8, 0, 0));
        player.setYRot(-90); player.setYHeadRot(-90);
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        player.setHealth(200);
        // FakePlayer never ticks down the normal login protection, unlike a real player.
        var protection = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        protection.setAccessible(true); protection.setInt(player, 0);
        helper.getLevel().addNewPlayer(player);
        helper.assertTrue(hound.tryStartSurfaceStalking(player), "Could not start physical crowding fixture");
        helper.onEachTick(() -> {
            if (hound.isRemoved() || !hound.isSurfaceStalkAttack()) return;
            helper.assertTrue(hound.currentRoll == de.cadentem.pale_hound.entities.goals.Roll.CHASE && !hound.isFleeing,
                "Proximity targeting rerolled a committed stalk attack into fleeing");
        });
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(hound.isSurfaceStalkAttack() && !hound.isCrawling() && hound.getSpeedModifier() >= 1.0,
                "Committed surface attack retained the slow stalking posture/speed");
            helper.assertTrue(hound.distanceTo(player) < 4, "Surface attack did not physically sprint toward the player");
        });
        helper.runAfterDelay(130, () -> {
            try {
                helper.assertTrue(!hound.isSurfaceStalking() && hound.getTarget() == player, "Stalker did not retain the close player target");
                helper.assertTrue(player.getHealth() < 200, "Close approach changed flags but never produced a real melee hit: position=" + hound.position());
            } finally { clean(helper, hound, player); }
            helper.succeed();
        });
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "stalking_motion")
    public static void surfaceCrowdingIgnoresProtectedPlayersAndBlockedSight(GameTestHelper helper) {
        var hound = hound(helper, 64, 64); hound.setNoAi(true);
        var watched = player(helper, hound.position().add(40, 0, 0));
        var intruder = player(helper, hound.position().add(8, 0, 0));
        try {
            helper.assertTrue(hound.tryStartSurfaceStalking(watched), "Could not start watching");
            intruder.setGameMode(GameType.CREATIVE); hound.tick();
            helper.assertTrue(hound.isSurfaceStalking(), "Ordinary Creative player triggered an attack");
            intruder.setGameMode(GameType.SPECTATOR); hound.tick();
            helper.assertTrue(hound.isSurfaceStalking(), "Spectator triggered an attack");
            intruder.setGameMode(GameType.SURVIVAL);
            for (int y = 2; y <= 5; ++y) helper.setBlock(66, y, 64, Blocks.STONE);
            hound.tick(); helper.assertTrue(hound.isSurfaceStalking(), "Close player behind a solid wall triggered an attack");
            for (int y = 2; y <= 5; ++y) helper.setBlock(66, y, 64, Blocks.AIR);
            hound.tick();
            helper.assertTrue(hound.isSurfaceStalkAttack() && hound.getTarget() == intruder, "Nearby eligible intruder did not take priority over distant watched player");
        } finally {
            clean(helper, hound, watched);
            helper.getLevel().removePlayerImmediately(intruder, Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

}
