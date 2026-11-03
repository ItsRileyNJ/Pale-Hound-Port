package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.PaleHoundEntity;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import de.cadentem.pale_hound.network.CaveSound;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("pale_hound")
@PrefixGameTestTemplate(false)
public final class PortGameTests {
    private static PaleHoundEntity spawn(GameTestHelper helper) {
        PaleHoundEntity hound = ModEntityTypes.PALE_HOUND.get().create(helper.getLevel());
        helper.assertTrue(hound != null, "Entity factory returned null");
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        helper.getLevel().addFreshEntity(hound);
        return hound;
    }

    @GameTest(template = "port_test")
    public static void attributesAndDimensions(GameTestHelper helper) {
        var hound = spawn(helper);
        helper.assertTrue(hound.getMaxHealth() == ServerConfig.MAX_HEALTH.get().floatValue(), "Configured health missing");
        helper.assertTrue(hound.getAttributeValue(Attributes.ATTACK_DAMAGE) == ServerConfig.ATTACK_DAMAGE.get(), "Configured attack missing");
        double expectedSpeed = ServerConfig.MOVEMENT_SPEED.get() * 0.8;
        helper.assertTrue(Math.abs(hound.getAttributeValue(Attributes.MOVEMENT_SPEED) - expectedSpeed) < 0.000001,
            "Configured movement speed must be reduced by 20 percent");
        hound.applyConfigAttributes();
        helper.assertTrue(Math.abs(hound.getAttributeValue(Attributes.MOVEMENT_SPEED) - expectedSpeed) < 0.000001,
            "Reapplying attributes must not compound the slowdown");
        helper.assertTrue(hound.maxUpStep() == 1.0F, "Step height migration differs");
        helper.assertTrue(hound.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY) == ServerConfig.DEPTH_STRIDER_BONUS.get() / 3, "Water efficiency differs");
        hound.setCrawling(true);
        helper.assertTrue(hound.getDimensions(Pose.STANDING).height() == 0.5F, "Crawl dimensions incorrect");
        hound.setCrawling(false);
        helper.assertTrue(hound.getDimensions(Pose.STANDING).height() == 2.7F, "Standing dimensions not restored");
        hound.discard();
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void immunityAndStaring(GameTestHelper helper) {
        var hound = spawn(helper);
        float health = hound.getHealth();
        hound.hurt(hound.damageSources().fall(), 10);
        hound.hurt(hound.damageSources().inWall(), 10);
        helper.assertTrue(hound.getHealth() == health, "Fall or suffocation bypassed immunity");
        hound.pleaseStopMoving = true;
        Vec3 position = hound.position();
        hound.travel(new Vec3(1, 0, 1));
        helper.assertTrue(position.equals(hound.position()), "Stare travel mixin did not cancel movement");
        hound.discard();
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void navigationRestoresCrawling(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setCrawling(false);
        hound.getNavigation().createPath(hound.blockPosition().offset(2, 0, 2), 0);
        helper.assertTrue(!hound.isCrawling(), "Path mixin leaked temporary crawling state");
        hound.setCrawling(true);
        hound.getNavigation().createPath(hound.blockPosition().offset(2, 0, 2), 0);
        helper.assertTrue(hound.isCrawling(), "Path mixin lost original crawling state");
        hound.discard();
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void crawlsInLowTunnels(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoAi(true);
        hound.setNoGravity(true);
        var roof = hound.blockPosition().above(2);
        helper.getLevel().setBlockAndUpdate(roof, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        hound.tick();
        helper.assertTrue(hound.isCrawling(), "Two-block-high tunnel should trigger crawling");
        helper.assertTrue(hound.getDimensions(Pose.STANDING).height() == 0.5F, "Tunnel crawl hitbox incorrect");
        helper.getLevel().removeBlock(roof, false);
        hound.tick();
        helper.assertTrue(!hound.isCrawling(), "Open space should restore standing");
        hound.discard();
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void crawlsDuringCloseChases(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoAi(true);
        hound.setNoGravity(true);
        var target = net.minecraft.world.entity.EntityType.SHEEP.create(helper.getLevel());
        target.setPos(hound.position().add(6, 0, 0));
        hound.setTarget(target);
        hound.currentRoll = de.cadentem.pale_hound.entities.goals.Roll.CHASE;
        hound.setAggressive(true);
        hound.tick();
        helper.assertTrue(hound.isCrawling(), "Close chase should trigger crawling");
        helper.assertTrue(hound.getSpeedModifier() == 0.35, "Crawl speed changed");
        target.setPos(hound.position().add(20, 0, 0));
        hound.tick();
        helper.assertTrue(!hound.isCrawling(), "Distant chase should restore standing");
        target.setPos(hound.position().add(6, 0, 0));
        hound.currentRoll = de.cadentem.pale_hound.entities.goals.Roll.STARE;
        hound.tick();
        helper.assertTrue(!hound.isCrawling(), "Proximity alone should not trigger chase crawling");
        hound.setTarget(null);
        hound.discard();
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void surfaceStalkingIsPassive(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoGravity(true);
        while (hound.blockPosition().getY() > helper.getLevel().getMinBuildHeight()
            && !helper.getLevel().getBlockState(hound.blockPosition().below()).isSolid())
            hound.setPos(hound.position().add(0, -1, 0));
        var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "StalkTest")) {
                @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
            };
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(hound.position().add(20, 0, 0));
        helper.getLevel().addNewPlayer(player);
        try {
            hound.setAggressive(true);
            helper.assertTrue(!hound.tryStartSurfaceStalking(player), "Stalking must not interrupt attacks");
            hound.setAggressive(false);
            helper.assertTrue(hound.tryStartSurfaceStalking(player), "Idle surface hound should be able to stalk");
            hound.tick();
            helper.assertTrue(hound.isSurfaceStalking(), "Surface player should activate stalking without staring");
            helper.assertTrue(hound.getSurfaceStalkTarget() == player && hound.isCrawling(), "Stalking target or crawling missing");
            helper.assertTrue(!hound.isAggressive() && hound.getTarget() == null, "Stalking must suppress combat targeting");
            var goal = new de.cadentem.pale_hound.entities.goals.PaleHoundSurfaceStalkGoal(hound);
            goal.start();
            goal.tick();
            helper.assertTrue(hound.getNavigation().isDone(), "Hound should wait at stalking distance");
            player.setPos(hound.position().add(40, 0, 0));
            goal.start();
            goal.tick();
            var followPath = hound.getNavigation().getPath();
            helper.assertTrue(followPath != null && !followPath.isDone(), "Stalking should path toward a distant player");
            helper.assertTrue(followPath.getTarget().distSqr(player.blockPosition()) >= 16 * 16, "Follow path should leave room before the player");
            player.setPos(hound.position().add(1, 0, 0));
            goal.start();
            goal.tick();
            var retreatPath = hound.getNavigation().getPath();
            helper.assertTrue(retreatPath != null && retreatPath.getTarget().distSqr(player.blockPosition()) > 1,
                "Approaching player should trigger a path away");
            hound.tick();
            float health = player.getHealth();
            helper.assertTrue(!hound.doHurtTarget(player) && player.getHealth() == health, "Stalking hound attacked nearby player");
            helper.assertTrue(hound.getTarget() == null && !hound.isAggressive(), "Proximity goal bypassed stalking");
            // Keep the test synchronous: advance the entity through the whole lifecycle.
            hound.setNoAi(true);
            int ticks = 0;
            int minimum = de.cadentem.pale_hound.util.Utils.scalePresenceTicks(600);
            int maximum = de.cadentem.pale_hound.util.Utils.scalePresenceTicks(900);
            while (!hound.isSurfaceStalkRetreating() && ticks++ < maximum + 1) hound.tick();
            helper.assertTrue(ticks + 2 >= minimum && ticks + 2 <= maximum, "Stalking duration should respect the presence multiplier");
            helper.assertTrue(hound.isSurfaceStalkRetreating() && hound.isFleeing, "Stalking must transition to retreat");
            helper.assertTrue(!hound.isCrawling() && hound.getSpeedModifier() == 1.2, "Retreat should use running posture and speed");
            helper.assertTrue(!hound.doHurtTarget(player), "Retreat must remain passive");
            for (int i = 0; i < 60; i++) hound.tick();
            helper.assertTrue(hound.isRemoved(), "Hound must despawn after the three-second retreat");
        } finally {
            hound.discard();
            helper.getLevel().removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void prefersStalkingBeforeProximityAttack(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoGravity(true);
        var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "SpawnStalkTest"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(hound.position().add(6, 0, 0));
        helper.getLevel().addNewPlayer(player);
        try {
            // Seed zero rolls about 0.73: rejected by the old 50% threshold, accepted by 90%.
            hound.getRandom().setSeed(0);
            hound.tick();
            helper.assertTrue(hound.isSurfaceStalking(), "Eligible spawn should prefer stalking on its first tick");
            helper.assertTrue(hound.isCrawling() && !hound.isAggressive() && hound.getTarget() == null,
                "Proximity attack should not win over the initial stalking choice");
            helper.assertTrue(!hound.doHurtTarget(player), "Preferred stalking must remain passive");
        } finally {
            hound.discard();
            helper.getLevel().removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void shorterSpawnWaitsRespectSavedSettings(GameTestHelper helper) {
        int min = ServerConfig.CAN_SPAWN_MIN.get();
        int max = ServerConfig.CAN_SPAWN_MAX.get();
        int cooldown = ServerConfig.CAN_SPAWN_COOLDOWN.get();
        double cooldownChance = ServerConfig.CAN_SPAWN_COOLDOWN_CHANCE.get();
        double multiplier = ServerConfig.SPAWN_TIMER_MULTIPLIER.get();
        try {
            helper.assertTrue(multiplier == 0.8, "Existing config should receive the new 0.8 multiplier default");
            ServerConfig.CAN_SPAWN_MIN.set(100);
            ServerConfig.CAN_SPAWN_MAX.set(100);
            ServerConfig.CAN_SPAWN_COOLDOWN_CHANCE.set(0.0);
            var timer = new de.cadentem.pale_hound.util.Timer();
            helper.assertTrue(timer.targetSpawn >= 1600 && timer.targetSpawn < 1616,
                "Custom 100-second spawn window should be scaled to about 80 seconds");
            timer.currentSpawn = timer.targetSpawn - 1;
            helper.assertTrue(!timer.isSpawnTimerReached(), "Spawn timer fired early");
            timer.currentSpawn++;
            helper.assertTrue(timer.isSpawnTimerReached(), "Shortened spawn timer did not become ready");
            helper.assertTrue(timer.targetNoise >= ServerConfig.RESET_NOISE_MIN.get() * 20
                && timer.targetNoise < (ServerConfig.RESET_NOISE_MAX.get() + 1) * 20, "Noise timing was changed");
            ServerConfig.CAN_SPAWN_COOLDOWN.set(1400);
            ServerConfig.CAN_SPAWN_COOLDOWN_CHANCE.set(1.0);
            timer.resetSpawnTimer();
            helper.assertTrue(timer.targetSpawn == 22400, "Long cooldown should also be 20% shorter");
            ServerConfig.SPAWN_TIMER_MULTIPLIER.set(1.0);
            timer.resetSpawnTimer();
            helper.assertTrue(timer.targetSpawn == 28000, "Multiplier 1.0 should restore original cooldown");
        } finally {
            ServerConfig.CAN_SPAWN_MIN.set(min);
            ServerConfig.CAN_SPAWN_MAX.set(max);
            ServerConfig.CAN_SPAWN_COOLDOWN.set(cooldown);
            ServerConfig.CAN_SPAWN_COOLDOWN_CHANCE.set(cooldownChance);
            ServerConfig.SPAWN_TIMER_MULTIPLIER.set(multiplier);
        }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void directGazeTriggersRetreatAndShorterRespawn(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoAi(true);
        hound.setNoGravity(true);
        var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "GazeTest"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(hound.position().add(4, 0, 0));
        helper.getLevel().addNewPlayer(player);
        double chance = ServerConfig.CAN_SPAWN_COOLDOWN_CHANCE.get();
        int cooldown = ServerConfig.CAN_SPAWN_COOLDOWN.get();
        double multiplier = ServerConfig.SPAWN_TIMER_MULTIPLIER.get();
        var wall = hound.blockPosition().offset(2, 0, 0);
        try {
            ServerConfig.CAN_SPAWN_COOLDOWN_CHANCE.set(1.0);
            ServerConfig.CAN_SPAWN_COOLDOWN.set(1000);
            ServerConfig.SPAWN_TIMER_MULTIPLIER.set(0.8);
            helper.assertTrue(hound.tryStartSurfaceStalking(player), "Could not start stalking");
            hound.tick();
            helper.assertTrue(!hound.isSurfaceStalkRetreating(), "Looking away should not interrupt stalking");
            Vec3 direction = hound.getEyePosition().subtract(player.getEyePosition());
            player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            player.setXRot((float)-Math.toDegrees(Math.atan2(direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z))));
            player.setYHeadRot(player.getYRot());
            helper.getLevel().setBlockAndUpdate(wall, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(wall.above(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            hound.tick();
            helper.assertTrue(!hound.isSurfaceStalkRetreating(), "Gaze through a wall should not count");
            helper.getLevel().removeBlock(wall, false);
            helper.getLevel().removeBlock(wall.above(), false);
            String dimension = helper.getLevel().dimension().location().toString();
            helper.assertTrue(hound.isLookingAtMe(player, true), "Clear direct gaze was not detected: LOS=" + player.hasLineOfSight(hound) + " view=" + player.getViewVector(1) + " delta=" + hound.getEyePosition().subtract(player.getEyePosition()).normalize() + " valid=" + de.cadentem.pale_hound.util.Utils.isValidTarget(player) + " range=" + ServerConfig.SPOTTING_RANGE.get());
            var spawnBefore = de.cadentem.pale_hound.PaleHound.getTimer(dimension, "spawn");
            var noiseBefore = de.cadentem.pale_hound.PaleHound.getTimer(dimension, "noise");
            hound.tick();
            helper.assertTrue(hound.isSurfaceStalkRetreating() && hound.isFleeing, "Direct eye contact should trigger immediate retreat");
            helper.assertTrue(!hound.isCrawling(), "Spotted hound should run instead of crawl");
            helper.assertTrue(spawnBefore.equals(de.cadentem.pale_hound.PaleHound.getTimer(dimension, "spawn")),
                "Respawn countdown should start after despawning, not before");
            for (int i = 0; i < 60; i++) hound.tick();
            helper.assertTrue(hound.isRemoved(), "Spotted hound should despawn after running away");
            var nextSpawn = de.cadentem.pale_hound.PaleHound.getTimer(dimension, "spawn");
            helper.assertTrue(nextSpawn.getFirst() == 0 && nextSpawn.getSecond() == 4000,
                "Next spawn wait should be 25% of the configured 800-second effective cooldown");
            helper.assertTrue(noiseBefore.equals(de.cadentem.pale_hound.PaleHound.getTimer(dimension, "noise")), "Noise timer should not be reset");
            hound.tick();
            helper.assertTrue(nextSpawn.equals(de.cadentem.pale_hound.PaleHound.getTimer(dimension, "spawn")), "Spotted bonus must apply only once");
            var ordinary = new de.cadentem.pale_hound.util.Timer();
            helper.assertTrue(ordinary.targetSpawn == 16000, "Later normal spawn resets should retain regular timing");
        } finally {
            hound.discard();
            helper.getLevel().removeBlock(wall, false);
            helper.getLevel().removeBlock(wall.above(), false);
            helper.getLevel().removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            ServerConfig.CAN_SPAWN_COOLDOWN_CHANCE.set(chance);
            ServerConfig.CAN_SPAWN_COOLDOWN.set(cooldown);
            ServerConfig.SPAWN_TIMER_MULTIPLIER.set(multiplier);
        }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void onlyAllowedTargetsCanBeAttacked(GameTestHelper helper) {
        var hound = spawn(helper);
        try {
            for (var type : java.util.List.of(net.minecraft.world.entity.EntityType.SHEEP,
                net.minecraft.world.entity.EntityType.VILLAGER, net.minecraft.world.entity.EntityType.IRON_GOLEM,
                net.minecraft.world.entity.EntityType.WOLF)) {
                var target = (net.minecraft.world.entity.LivingEntity)type.create(helper.getLevel());
                helper.assertTrue(de.cadentem.pale_hound.util.PaleHoundTargets.isAllowed(target), "Allowed target rejected: " + type);
                hound.setTarget(target);
                helper.assertTrue(hound.getTarget() == target, "Allowed target was not assigned");
                target.discard();
            }
            for (var type : java.util.List.of(net.minecraft.world.entity.EntityType.COW, net.minecraft.world.entity.EntityType.ZOMBIE,
                net.minecraft.world.entity.EntityType.PILLAGER,
                net.minecraft.world.entity.EntityType.HORSE, net.minecraft.world.entity.EntityType.WANDERING_TRADER)) {
                var target = (net.minecraft.world.entity.LivingEntity)type.create(helper.getLevel());
                float health = target.getHealth();
                hound.setTarget(target);
                helper.assertTrue(hound.getTarget() == null && !hound.canAttack(target), "Excluded target assigned: " + type);
                helper.assertTrue(!hound.doHurtTarget(target) && target.getHealth() == health, "Excluded mob took damage: " + type);
                hound.setLastHurtByMob(target);
                helper.assertTrue(!new de.cadentem.pale_hound.entities.goals.CustomHurtByTargetGoal(hound).canUse(),
                    "Retaliation allowed an excluded attacker: " + type);
                target.discard();
            }
            var pet = net.minecraft.world.entity.EntityType.WOLF.create(helper.getLevel());
            pet.setTame(true, false);
            helper.assertTrue(de.cadentem.pale_hound.util.PaleHoundTargets.isAllowed(pet), "Tamed wolf pet should be allowed");
            pet.discard();
            var horse = net.minecraft.world.entity.EntityType.HORSE.create(helper.getLevel());
            horse.getPersistentData().putUUID("FFMCaravan", java.util.UUID.randomUUID());
            helper.assertTrue(de.cadentem.pale_hound.util.PaleHoundTargets.isAllowed(horse), "Marked caravan horse should be allowed");
            horse.discard();
            // Run these assertions with the supplied optional mod too, without making it required.
            for (String name : java.util.List.of("defender", "caravan_villager", "caravan_trader", "caravan_mule")) {
                var id = ResourceLocation.fromNamespaceAndPath("villagerscontract", name);
                if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id)) continue;
                var target = (net.minecraft.world.entity.LivingEntity)net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id).create(helper.getLevel());
                helper.assertTrue(de.cadentem.pale_hound.util.PaleHoundTargets.isAllowed(target), "Optional mod target rejected: " + id);
                hound.setTarget(target);
                helper.assertTrue(hound.getTarget() == target && hound.canAttack(target), "Optional mod target selection failed: " + id);
                target.discard();
            }
        } finally { hound.discard(); }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void extendedLifetimeRespectsConfiguration(GameTestHelper helper) {
        int lifetime = ServerConfig.TIME_UNTIL_LEAVE.get();
        double multiplier = ServerConfig.PRESENCE_TIME_MULTIPLIER.get();
        PaleHoundEntity hound = null;
        try {
            ServerConfig.TIME_UNTIL_LEAVE.set(2);
            ServerConfig.PRESENCE_TIME_MULTIPLIER.set(3.0);
            hound = spawn(helper);
            hound.setNoAi(true);
            hound.setNoGravity(true);
            for (int tick = 0; tick < 41; ++tick) hound.tick();
            helper.assertTrue(!hound.isRemoved(), "Hound should outlive its original unscaled lifetime");
            for (int tick = 41; tick < 120; ++tick) hound.tick();
            helper.assertTrue(hound.isRemoved(), "Scaled lifetime should still expire");
        } finally {
            if (hound != null) hound.discard();
            ServerConfig.TIME_UNTIL_LEAVE.set(lifetime);
            ServerConfig.PRESENCE_TIME_MULTIPLIER.set(multiplier);
        }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void navigationReplansAndStopsCleanly(GameTestHelper helper) {
        for (int x = 0; x < 8; ++x) for (int z = 0; z < 8; ++z)
            helper.setBlock(x, 0, z, net.minecraft.world.level.block.Blocks.STONE);
        var hound = spawn(helper);
        hound.setNoAi(true);
        hound.setNoGravity(true);
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 3))));
        try {
            var navigation = hound.getNavigation();
            var target = helper.absolutePos(new BlockPos(6, 1, 3));
            var initial = navigation.createPath(target, 0);
            helper.assertTrue(initial != null && initial.canReach(), "Initial open path should reach destination");
            navigation.tick();
            helper.assertTrue(navigation.getTargetPos() == null, "Planning should not replace the active navigation destination");
            helper.assertTrue(!hound.getMoveControl().hasWanted(), "Merely planning a path should not start climbing movement");
            helper.assertTrue(navigation.moveTo(initial, 0.6), "Could not start initial route");
            for (int y = 1; y <= 3; ++y) for (int z = 1; z <= 5; ++z)
                helper.setBlock(4, y, z, net.minecraft.world.level.block.Blocks.STONE);
            for (int tick = 0; tick < 42; ++tick) navigation.tick();
            var rerouted = navigation.getPath();
            helper.assertTrue(rerouted != null && rerouted != initial && rerouted.canReach(), "Stuck navigation should find a fresh route around the wall");
            for (int i = 0; i < rerouted.getNodeCount(); ++i) {
                var node = rerouted.getNode(i).asBlockPos();
                helper.assertTrue(helper.getLevel().getBlockState(node).getCollisionShape(helper.getLevel(), node).isEmpty(), "Replanned route intersects wall");
            }
            navigation.stop();
            hound.getMoveControl().tick();
            navigation.tick();
            helper.assertTrue(navigation.isDone() && navigation.getTargetPos() == null && !hound.getMoveControl().hasWanted(), "Stopped climbing navigator resumed its stale destination");
            // A safe fallback should beat an impassable first candidate.
            var route = de.cadentem.pale_hound.entities.navigation.HoundRoutes.findReachable(hound,
                java.util.List.of(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 3))),
                    Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 6)))));
            helper.assertTrue(route != null && route.canReach(), "Waypoint search should try another reachable candidate");
            helper.setBlock(2, 0, 6, net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
            var safeRoute = de.cadentem.pale_hound.entities.navigation.HoundRoutes.findReachable(hound,
                java.util.List.of(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 6))),
                    Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 1)))));
            helper.assertTrue(safeRoute != null && !safeRoute.getTarget().equals(helper.absolutePos(new BlockPos(2, 1, 6))),
                "Waypoint selection should reject magma and use safe ground");
        } finally { hound.discard(); }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void roamingExploresBeyondOriginalRadius(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoAi(true);
        hound.setNoGravity(true);
        while (hound.blockPosition().getY() > helper.getLevel().getMinBuildHeight()
            && !helper.getLevel().getBlockState(hound.blockPosition().below()).isSolid())
            hound.setPos(hound.position().add(0, -1, 0));
        try {
            hound.getRandom().setSeed(0);
            var roaming = new de.cadentem.pale_hound.entities.goals.PaleHoundRoamGoal(hound, 0.35);
            boolean exploredFurther = false;
            for (int attempt = 0; attempt < 16; ++attempt) {
                var destination = roaming.getPosition();
                if (destination == null) continue;
                double dx = destination.x - hound.getX();
                double dz = destination.z - hound.getZ();
                if (Math.abs(dx) > 10 || Math.abs(dz) > 10) exploredFurther = true;
                helper.assertTrue(Math.abs(dx) <= ServerConfig.ROAM_RADIUS.get() + 1
                    && Math.abs(dz) <= ServerConfig.ROAM_RADIUS.get() + 1, "Roaming exceeded configured search bounds");
            }
            helper.assertTrue(exploredFurther, "Roaming should choose reachable destinations beyond the original ten-block search");
        } finally { hound.discard(); }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void highlightCommandPermissions(GameTestHelper helper) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var hound = spawn(helper);
        var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "HighlightTest"));
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        try {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var ordinary = player.createCommandSourceStack().withPermission(0);
            boolean denied = false;
            try { dispatcher.execute("pale_hound highlight", ordinary); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            helper.assertTrue(denied, "Non-OP survival player should not highlight");
            helper.assertTrue(dispatcher.execute("pale_hound highlight", ordinary.withPermission(2)) >= 1,
                "Survival operator should be able to highlight");
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var creative = player.createCommandSourceStack().withPermission(0);
            helper.assertTrue(dispatcher.execute("pale_hound highlight 45", creative) >= 1,
                "Creative player without OP should be able to highlight");
            hound.setInvisible(true);
            hound.hasSpawned = true;
            helper.assertTrue(dispatcher.execute("pale_hound highlight", creative) >= 1,
                "Naturally spawned invisible Hound should be included");
            var nether = helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            helper.assertTrue(dispatcher.execute("pale_hound highlight", creative.withLevel(nether)) >= 1,
                "Highlight should search the requesting player's dimension even with a redirected command context");
            var pos = hound.position().add(2, 2, 0);
            String summon = String.format(java.util.Locale.ROOT,
                "summon pale_hound:pale_hound %.3f %.3f %.3f {NoAI:1b,NoGravity:1b,Tags:[\"highlight_summon_test\"]}", pos.x, pos.y, pos.z);
            helper.assertTrue(dispatcher.execute(summon, creative.withPermission(2)) == 1,
                "Native summon command failed");
            var summoned = helper.getLevel().getEntities(ModEntityTypes.PALE_HOUND.get(),
                entity -> entity.getTags().contains("highlight_summon_test"));
            try {
                helper.assertTrue(!summoned.isEmpty(), "Summoned Hound missing from registered-type lookup");
                hound.discard();
                helper.assertTrue(dispatcher.execute("pale_hound highlight", creative) >= 1,
                    "Summoned Hound should also be highlighted");
            } finally { summoned.forEach(PaleHoundEntity::discard); }
            denied = false;
            try { dispatcher.execute("pale_hound get_timer minecraft:overworld spawn", creative); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            helper.assertTrue(denied, "Creative highlight access should not grant other admin commands");
            helper.assertTrue(dispatcher.execute("pale_hound highlight off", creative) == 1, "Highlight off should work");
            helper.assertTrue(!hound.isCurrentlyGlowing(), "Private outline must not set global glowing state");
        } finally { hound.discard(); player.discard(); }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void highlightPayloadRoundTrip(GameTestHelper helper) {
        var ids = java.util.List.of(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        var packet = new de.cadentem.pale_hound.network.HoundHighlight(ids, 600);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            de.cadentem.pale_hound.network.HoundHighlight.STREAM_CODEC.encode(buffer, packet);
            var decoded = de.cadentem.pale_hound.network.HoundHighlight.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.equals(packet) && buffer.readableBytes() == 0, "Highlight payload changed in transit");
            buffer.clear();
            buffer.writeVarInt(600);
            buffer.writeVarInt(1025);
            boolean rejected = false;
            try { de.cadentem.pale_hound.network.HoundHighlight.STREAM_CODEC.decode(buffer); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "Oversized highlight list should be rejected");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void staysLowUntilEntireBodyClearsRoof(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoAi(true);
        hound.setNoGravity(true);
        try {
            hound.setPos(helper.absolutePos(new BlockPos(4, 1, 3)).getX() + 0.9,
                helper.absolutePos(new BlockPos(4, 1, 3)).getY(), helper.absolutePos(new BlockPos(4, 1, 3)).getZ() + 0.5);
            helper.setBlock(5, 2, 3, net.minecraft.world.level.block.Blocks.STONE);
            hound.setCrawling(true);
            hound.tick();
            helper.assertTrue(hound.isCrawling(), "Body still overlaps roof edge and must not expand");
            hound.setCrawling(false);
            helper.assertTrue(hound.isCrawling(), "Path/goal cleanup must not force unsafe standing");
            helper.assertTrue(helper.getLevel().noCollision(hound, hound.getBoundingBox().deflate(0.00001)), "Low posture should remain collision-free");
            hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 1, 3))));
            hound.tick();
            helper.assertTrue(!hound.isCrawling(), "Hound should stand once its full body clears the roof");
        } finally { hound.discard(); }
        helper.succeed();
    }

    @GameTest(template = "tunnel_test", timeoutTicks = 240)
    public static void physicallyTraversesLowTunnel(GameTestHelper helper) {
        for (int x = 0; x < 12; ++x) for (int z = 0; z < 7; ++z)
            helper.setBlock(x, 0, z, net.minecraft.world.level.block.Blocks.STONE);
        for (int x = 0; x < 12; ++x) for (int y = 1; y <= 4; ++y) {
            helper.setBlock(x, y, 1, net.minecraft.world.level.block.Blocks.STONE);
            helper.setBlock(x, y, 5, net.minecraft.world.level.block.Blocks.STONE);
        }
        for (int x = 2; x <= 8; ++x) for (int z = 1; z <= 5; ++z)
            helper.setBlock(x, 2, z, net.minecraft.world.level.block.Blocks.STONE);
        var hound = new PaleHoundEntity(ModEntityTypes.PALE_HOUND.get(), helper.getLevel()) {
            @Override protected void registerGoals() {
                this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.Goal() {
                    @Override public boolean canUse() { return false; }
                });
                this.targetSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.Goal() {
                    @Override public boolean canUse() { return false; }
                });
            }
        };
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 3))));
        helper.getLevel().addFreshEntity(hound);
        var target = helper.absolutePos(new BlockPos(10, 1, 3));
        helper.assertTrue(hound.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8), "Tunnel path should be available");
        helper.onEachTick(() -> helper.assertTrue(helper.getLevel().noCollision(hound, hound.getBoundingBox().deflate(0.00001)), "Hound clipped into tunnel wall or ceiling"));
        helper.succeedWhen(() -> {
            helper.assertTrue(hound.distanceToSqr(Vec3.atBottomCenterOf(target)) < 1, "Hound has not crossed the low tunnel");
            hound.discard();
        });
    }

    @GameTest(template = "port_test")
    public static void roamingSurvivesInactivityAndRecoversIdleRoll(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoAi(true);
        hound.setNoGravity(true);
        while (hound.blockPosition().getY() > helper.getLevel().getMinBuildHeight()
            && !helper.getLevel().getBlockState(hound.blockPosition().below()).isSolid())
            hound.setPos(hound.position().add(0, -1, 0));
        int originalInterval = ServerConfig.ROAM_INTERVAL.get();
        try {
            hound.setNoActionTime(1000);
            hound.getRandom().setSeed(0);
            var roaming = new de.cadentem.pale_hound.entities.goals.PaleHoundRoamGoal(hound, 0.35);
            ServerConfig.ROAM_INTERVAL.set(10);
            boolean canRoam = false;
            for (int i = 0; i < 32 && !canRoam; ++i) canRoam = roaming.canUse();
            helper.assertTrue(canRoam && hound.getNoActionTime() == 1000, "Inactivity must not permanently disable roaming");
            hound.currentRoll = de.cadentem.pale_hound.entities.goals.Roll.FLEE;
            hound.getNavigation().stop();
            for (int i = 0; i < 100; ++i) hound.tick();
            helper.assertTrue(hound.currentRoll == de.cadentem.pale_hound.entities.goals.Roll.STROLL,
                "Targetless flee roll should recover instead of standing idle forever");
        } finally { hound.discard(); ServerConfig.ROAM_INTERVAL.set(originalInterval); }
        helper.succeed();
    }

    @GameTest(template = "tunnel_test", timeoutTicks = 240)
    public static void leavesHighCaveLedgeWithoutGettingStranded(GameTestHelper helper) {
        for (int x = 0; x < 12; ++x) for (int z = 0; z < 8; ++z)
            helper.setBlock(x, 0, z, net.minecraft.world.level.block.Blocks.STONE);
        for (int x = 3; x <= 5; ++x) for (int z = 2; z <= 4; ++z)
            for (int y = 1; y <= 5; ++y) helper.setBlock(x, y, z, net.minecraft.world.level.block.Blocks.STONE);
        var hound = new PaleHoundEntity(ModEntityTypes.PALE_HOUND.get(), helper.getLevel()) {
            @Override protected void registerGoals() {}
        };
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 6, 3))));
        helper.getLevel().addFreshEntity(hound);
        var target = helper.absolutePos(new BlockPos(9, 1, 3));
        var path = hound.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(), "Fall-immune Hound must be able to path down from a high cave ledge");
        helper.assertTrue(hound.getNavigation().moveTo(path, 0.8), "Could not begin ledge descent");
        helper.onEachTick(() -> helper.assertTrue(helper.getLevel().noCollision(hound, hound.getBoundingBox().deflate(0.00001)), "Ledge descent clipped into a wall"));
        helper.succeedWhen(() -> {
            helper.assertTrue(hound.distanceToSqr(Vec3.atBottomCenterOf(target)) < 1, "Hound has not left the ledge and resumed movement");
            hound.discard();
        });
    }

    @GameTest(template = "tunnel_test", timeoutTicks = 300)
    public static void crawlsAroundNarrowCaveCorner(GameTestHelper helper) {
        for (int x = 0; x < 12; ++x) for (int z = 0; z < 8; ++z)
            for (int y = 0; y <= 3; ++y) helper.setBlock(x, y, z, net.minecraft.world.level.block.Blocks.STONE);
        for (int x = 1; x <= 8; ++x) helper.setBlock(x, 1, 1, net.minecraft.world.level.block.Blocks.AIR);
        for (int z = 1; z <= 6; ++z) helper.setBlock(8, 1, z, net.minecraft.world.level.block.Blocks.AIR);
        var hound = new PaleHoundEntity(ModEntityTypes.PALE_HOUND.get(), helper.getLevel()) {
            @Override protected void registerGoals() {}
        };
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        helper.getLevel().addFreshEntity(hound);
        var target = helper.absolutePos(new BlockPos(8, 1, 6));
        helper.assertTrue(hound.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8), "No route around cave corner");
        helper.onEachTick(() -> helper.assertTrue(helper.getLevel().noCollision(hound, hound.getBoundingBox().deflate(0.00001)), "Cave turn clipped into a wall"));
        helper.succeedWhen(() -> {
            helper.assertTrue(hound.distanceToSqr(Vec3.atBottomCenterOf(target)) < 0.5, "Hound stuck at the cave corner");
            hound.discard();
        });
    }

    @GameTest(template = "port_test")
    public static void finishedCrawlPathDoesNotPushIntoWall(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoAi(true);
        hound.setNoGravity(true);
        hound.setCrawling(true);
        try {
            var start = hound.blockPosition();
            helper.setBlock(3, 2, 2, net.minecraft.world.level.block.Blocks.STONE);
            var stale = new net.minecraft.world.level.pathfinder.Path(java.util.List.of(
                new net.minecraft.world.level.pathfinder.Node(start.getX(), start.getY(), start.getZ())),
                start.east(3), false);
            hound.getNavigation().moveTo(stale, 0.6);
            stale.setNextNodeIndex(stale.getNodeCount());
            hound.getNavigation().tick();
            helper.assertTrue(hound.getMoveControl().getWantedX() == hound.getX()
                && hound.getMoveControl().getWantedZ() == hound.getZ(),
                "Finished crawl path must not keep spider-style direct movement into a wall");
        } finally { hound.discard(); }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void soundPayloadRoundTrip(GameTestHelper helper) {
        var packet = new CaveSound(ResourceLocation.fromNamespaceAndPath("pale_hound", "cavenoise_1"), new BlockPos(-12, 63, 28), 3, 0.75F);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            CaveSound.STREAM_CODEC.encode(buffer, packet);
            var decoded = CaveSound.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.soundResource.equals(packet.soundResource) && decoded.playerPosition.equals(packet.playerPosition)
                && decoded.volume == packet.volume && decoded.pitch == packet.pitch && buffer.readableBytes() == 0, "Sound payload changed in transit");
        } finally { buffer.release(); }
        helper.succeed();
    }
}
