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

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30)
    public static void surfaceStalkingIsPassive(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoGravity(true);
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 64))));
        while (hound.blockPosition().getY() > helper.getLevel().getMinBuildHeight()
            && !helper.getLevel().getBlockState(hound.blockPosition().below()).isSolid())
            hound.setPos(hound.position().add(0, -1, 0));
        var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "StalkTest")) {
                @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
            };
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(hound.position().add(40, 0, 0));
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
            player.setPos(hound.position().add(56, 0, 0));
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
            int minimum = de.cadentem.pale_hound.util.Utils.scalePresenceTicks(600) * ServerConfig.SURFACE_STALK_DURATION_MULTIPLIER.get().intValue();
            int maximum = de.cadentem.pale_hound.util.Utils.scalePresenceTicks(900) * ServerConfig.SURFACE_STALK_DURATION_MULTIPLIER.get().intValue();
            while (!hound.isSurfaceStalkRetreating() && ticks++ < maximum + 1) hound.tick();
            helper.assertTrue(ticks + 2 >= minimum && ticks + 2 <= maximum, "Stalking duration should respect the presence multiplier");
            helper.assertTrue(hound.isSurfaceStalkRetreating() && hound.isFleeing, "Stalking must transition to retreat");
            helper.assertTrue(!hound.isCrawling() && hound.getSpeedModifier() == 1.2, "Retreat should use running posture and speed");
            helper.assertTrue(!hound.doHurtTarget(player), "Retreat must remain passive");
            hound.setPos(hound.position().add(1024, 0, 0));
            for (int i = 0; i < 60; i++) hound.tick();
            helper.assertTrue(hound.isRemoved(), "Hound must despawn after running out of tracking range");
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
    public static void surfaceSpawnWaitLeavesCavesAndNoiseUnchanged(GameTestHelper helper) {
        var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "SurfaceTimerTest"));
        Vec3 surface = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        player.setPos(surface);
        double extra = ServerConfig.SURFACE_SPAWN_TIMER_MULTIPLIER.get();
        double common = ServerConfig.SURFACE_TIMER_MULTIPLIER.get();
        try {
            helper.assertTrue(extra == 2.0, "Existing configs should receive the new doubled surface wait");
            ServerConfig.SURFACE_TIMER_MULTIPLIER.set(1.0);
            var timer = new de.cadentem.pale_hound.util.Timer(player);
            timer.targetSpawn = 2000;
            timer.targetNoise = 1000;
            timer.currentSpawn = 2000;
            timer.currentNoise = 1000;
            helper.assertTrue(de.cadentem.pale_hound.util.Utils.isOnSurface(player), "Surface timer fixture should be above ground");
            helper.assertTrue(timer.getEffectiveSpawnTarget() == 4000 && !timer.isSpawnTimerReached(), "Surface spawning should wait twice as long");
            timer.currentSpawn = 3999;
            helper.assertTrue(!timer.isSpawnTimerReached(), "Surface timer fired before its threshold");
            timer.currentSpawn = 4000;
            helper.assertTrue(timer.isSpawnTimerReached(), "Surface timer did not become eligible at its threshold");
            helper.assertTrue(timer.getEffectiveNoiseTarget() == 1000 && timer.isNoiseTimerReached(), "New surface pacing must not slow crying/noise");
            ServerConfig.SURFACE_TIMER_MULTIPLIER.set(1.5);
            helper.assertTrue(timer.getEffectiveSpawnTarget() == 6000 && timer.getEffectiveNoiseTarget() == 1500,
                "The original common surface multiplier should still compose with the new spawn setting");
            player.setPos(surface.x, helper.getLevel().getMinBuildHeight(), surface.z);
            helper.assertTrue(!de.cadentem.pale_hound.util.Utils.isOnSurface(player), "Underground timer fixture should be below ground");
            timer.currentSpawn = 2000;
            helper.assertTrue(timer.getEffectiveSpawnTarget() == 2000 && timer.isSpawnTimerReached(), "Cave wait should retain its previous value");
            helper.assertTrue(timer.getEffectiveNoiseTarget() == 1000 && timer.isNoiseTimerReached(), "Cave noise timing should be unchanged");
            player.setPos(surface);
            ServerConfig.SURFACE_TIMER_MULTIPLIER.set(1.0);
            timer.targetSpawn = 500;
            helper.assertTrue(timer.getEffectiveSpawnTarget() == 1000, "The spotted quarter-wait must retain the surface pacing factor");
            ServerConfig.SURFACE_SPAWN_TIMER_MULTIPLIER.set(1.0);
            helper.assertTrue(timer.getEffectiveSpawnTarget() == 500, "Setting 1.0 should restore previous surface pacing");
        } finally {
            ServerConfig.SURFACE_SPAWN_TIMER_MULTIPLIER.set(extra);
            ServerConfig.SURFACE_TIMER_MULTIPLIER.set(common);
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30)
    public static void directGazeTriggersRetreatAndShorterRespawn(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoAi(true);
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 64))));
        hound.setNoGravity(true);
        var player = new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "GazeTest"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(hound.position().add(40, 0, 0));
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
            for (int i = 0; i < 160; ++i) hound.tick();
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
            for (int i = 0; i < 80; i++) hound.tick();
            helper.assertTrue(!hound.isRemoved(), "Visible Hound must not disappear on a three-second clock");
            hound.setPos(hound.position().add(1024, 0, 0));
            for (int i = 0; i < 20; i++) hound.tick();
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
    public static void crouchedAnimationFitsAvailableHeadroom(GameTestHelper helper) {
        var hound = spawn(helper);
        hound.setNoAi(true);
        hound.setNoGravity(true);
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 3))));
        hound.setCrawling(true);
        try {
            helper.assertTrue(hound.getCrawlPose() == PaleHoundEntity.CrawlPose.TALL, "Open space should use the tall intimidating crouch");
            helper.setBlock(4, 3, 3, net.minecraft.world.level.block.Blocks.STONE);
            helper.assertTrue(hound.getCrawlPose() == PaleHoundEntity.CrawlPose.TUNNEL, "Two-block tunnel should use the medium crouch");
            helper.setBlock(4, 2, 3, net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
                    net.minecraft.world.level.block.state.properties.SlabType.TOP));
            helper.assertTrue(hound.getCrawlPose() == PaleHoundEntity.CrawlPose.LOW, "Partial slab clearance should use the low crawl");
            helper.assertTrue(hound.getAnimationCrawlPose() == PaleHoundEntity.CrawlPose.LOW, "Low clearance must request compression promptly");
            helper.setBlock(4, 2, 3, net.minecraft.world.level.block.Blocks.AIR);
            helper.setBlock(4, 3, 3, net.minecraft.world.level.block.Blocks.AIR);
            helper.assertTrue(hound.getAnimationCrawlPose() == PaleHoundEntity.CrawlPose.LOW, "Pose rose immediately after leaving a low ceiling");
            hound.tickCount += 9;
            helper.assertTrue(hound.getAnimationCrawlPose() == PaleHoundEntity.CrawlPose.LOW, "Brief clearance should not cause pose oscillation");
            hound.tickCount++;
            helper.assertTrue(hound.getAnimationCrawlPose() == PaleHoundEntity.CrawlPose.TALL, "Sustained clearance did not allow the tall pose");
            helper.setBlock(5, 3, 3, net.minecraft.world.level.block.Blocks.STONE);
            hound.setPos(hound.position().add(0.4, 0, 0));
            helper.assertTrue(hound.getCrawlPose() == PaleHoundEntity.CrawlPose.TUNNEL, "A partial roof-edge overlap must prevent the tall pose");
            helper.setBlock(5, 3, 3, net.minecraft.world.level.block.Blocks.AIR);
            hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 3))));
            hound.yBodyRot = 0;
            helper.setBlock(4, 3, 4, net.minecraft.world.level.block.Blocks.STONE);
            helper.assertTrue(hound.getCrawlPose() == PaleHoundEntity.CrawlPose.TUNNEL,
                "Forward-projecting head should lower before reaching a tunnel entrance");
        } finally { hound.discard(); }
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

    @GameTest(template = "tunnel_test", timeoutTicks = 240, batch = "cave_navigation")
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

    @GameTest(template = "tunnel_test", timeoutTicks = 240, batch = "cave_navigation")
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

    @GameTest(template = "tunnel_test", timeoutTicks = 300, batch = "cave_navigation")
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
    public static void biomePolicyAndForestBonus(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var excluded = java.util.Set.of("badlands", "wooded_badlands", "eroded_badlands", "desert");
        for (var holder : registry.holders().toList()) {
            var id = holder.key().location();
            if (!id.getNamespace().equals("minecraft")) continue;
            helper.assertTrue(de.cadentem.pale_hound.util.PaleHoundBiomes.isAllowed(holder) == !excluded.contains(id.getPath()),
                "Unexpected biome eligibility: " + id);
        }
        double base = ServerConfig.SPAWN_CHANCE_PER_TICK.get();
        for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.FOREST,
            net.minecraft.world.level.biome.Biomes.DARK_FOREST, net.minecraft.world.level.biome.Biomes.TAIGA,
            net.minecraft.world.level.biome.Biomes.JUNGLE, net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP)) {
            double chance = de.cadentem.pale_hound.util.PaleHoundBiomes.spawnChance(registry.getHolderOrThrow(key));
            helper.assertTrue(Math.abs(chance - Math.min(1, base * ServerConfig.FOREST_SPAWN_CHANCE_MULTIPLIER.get())) < 0.0000001,
                "Forested biome did not receive its bonus: " + key.location());
        }
        helper.assertTrue(de.cadentem.pale_hound.util.PaleHoundBiomes.spawnChance(
            registry.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS)) == base, "Plains should retain base chance");
        helper.assertTrue(de.cadentem.pale_hound.util.PaleHoundBiomes.spawnChance(
            registry.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.WOODED_BADLANDS)) == 0,
            "Wooded badlands must remain blocked despite having trees");
        helper.succeed();
    }

    private static net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> testBiome(GameTestHelper helper, String path,
        net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> tag) {
        var registry = new net.minecraft.core.MappedRegistry<net.minecraft.world.level.biome.Biome>(
            net.minecraft.core.registries.Registries.BIOME, com.mojang.serialization.Lifecycle.stable());
        var value = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
            .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).value();
        var holder = registry.register(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("test", path)), value, net.minecraft.core.RegistrationInfo.BUILT_IN);
        registry.freeze();
        registry.bindTags(tag == null ? java.util.Map.of() : java.util.Map.of(tag, java.util.List.of(holder)));
        return holder;
    }

    @GameTest(template = "port_test")
    public static void moddedBiomeTagsAndNames(GameTestHelper helper) {
        for (String path : java.util.List.of("red_mesa", "rocky_badlands", "white_desert"))
            helper.assertTrue(!de.cadentem.pale_hound.util.PaleHoundBiomes.isAllowed(testBiome(helper, path, null)),
                "Untagged modded desert/Mesa variant should be blocked: " + path);
        var dry = testBiome(helper, "dry_basin", net.neoforged.neoforge.common.Tags.Biomes.IS_DESERT);
        helper.assertTrue(!de.cadentem.pale_hound.util.PaleHoundBiomes.isAllowed(dry), "Modded desert tag must be recognized");
        var woods = testBiome(helper, "enchanted_woods", de.cadentem.pale_hound.util.PaleHoundBiomes.FORESTED);
        helper.assertTrue(de.cadentem.pale_hound.util.PaleHoundBiomes.isAllowed(woods), "Modded forest should be eligible");
        helper.assertTrue(de.cadentem.pale_hound.util.PaleHoundBiomes.spawnChance(woods)
            == Math.min(1, ServerConfig.SPAWN_CHANCE_PER_TICK.get() * ServerConfig.FOREST_SPAWN_CHANCE_MULTIPLIER.get()),
            "Modded forest tag should receive the same bonus");
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void oldBiomeBlacklistMigratesOnce(GameTestHelper helper) {
        var original = ServerConfig.SURFACE_BIOMES.get();
        boolean whitelist = ServerConfig.SURFACE_BIOMES_IS_WHITELIST.get();
        boolean override = ServerConfig.OVERRIDE_BIOME_DATAPACK_CONFIG.get();
        try {
            ServerConfig.OVERRIDE_BIOME_DATAPACK_CONFIG.set(true);
            ServerConfig.SURFACE_BIOMES_IS_WHITELIST.set(false);
            ServerConfig.SURFACE_BIOMES.set(ServerConfig.LEGACY_SURFACE_BIOME_BLACKLIST);
            helper.assertTrue(ServerConfig.migrateDefaultBiomeBlacklist(), "Old default blacklist should migrate");
            helper.assertTrue(ServerConfig.SURFACE_BIOMES.get().equals(ServerConfig.DEFAULT_SURFACE_BIOME_BLACKLIST), "Incorrect migrated blacklist");
            helper.assertTrue(!ServerConfig.migrateDefaultBiomeBlacklist(), "Migration must be idempotent");
            ServerConfig.SURFACE_BIOMES.set(java.util.List.of("minecraft:forest"));
            helper.assertTrue(!ServerConfig.migrateDefaultBiomeBlacklist(), "A custom blacklist must be retained");
            ServerConfig.SURFACE_BIOMES_IS_WHITELIST.set(true);
            ServerConfig.SURFACE_BIOMES.set(ServerConfig.LEGACY_SURFACE_BIOME_BLACKLIST);
            helper.assertTrue(!ServerConfig.migrateDefaultBiomeBlacklist(), "A configured whitelist must not be rewritten");
        } finally {
            ServerConfig.SURFACE_BIOMES.set(original);
            ServerConfig.SURFACE_BIOMES_IS_WHITELIST.set(whitelist);
            ServerConfig.OVERRIDE_BIOME_DATAPACK_CONFIG.set(override);
        }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void actualSpawnSiteRespectsBiomeExclusion(GameTestHelper helper) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(4, 1, 3));
        var originalBiome = level.getBiome(pos).unwrapKey().orElseThrow().location();
        var source = level.getServer().createCommandSourceStack().withLevel(level).withPermission(4).withSuppressedOutput();
        String fill = "fillbiome " + (pos.getX() - 2) + " " + (pos.getY() - 2) + " " + (pos.getZ() - 2) + " "
            + (pos.getX() + 2) + " " + (pos.getY() + 2) + " " + (pos.getZ() + 2) + " ";
        var commands = level.getServer().getCommands().getDispatcher();
        var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "BiomeSpawnTest"));
        long originalTime = level.getDayTime();
        int distance = ServerConfig.SPAWN_DISTANCE.get();
        boolean checkPath = ServerConfig.CHECK_PATH_TO_SPAWN.get();
        try {
            helper.setBlock(4, 0, 3, net.minecraft.world.level.block.Blocks.STONE);
            for (int y = 1; y <= 4; ++y) helper.setBlock(4, y, 3, net.minecraft.world.level.block.Blocks.AIR);
            ServerConfig.SPAWN_DISTANCE.set(0);
            ServerConfig.CHECK_PATH_TO_SPAWN.set(false);
            level.setDayTime(18000);
            level.updateSkyBrightness();
            commands.execute(fill + "minecraft:desert", source);
            helper.assertTrue(level.getBiome(pos).is(net.minecraft.world.level.biome.Biomes.DESERT), "Desert test biome was not applied");
            var blocked = de.cadentem.pale_hound.util.Utils.trySpawnMob(player, ModEntityTypes.PALE_HOUND.get(),
                net.minecraft.world.entity.MobSpawnType.TRIGGERED, level, pos, 1, 0, 1,
                net.minecraft.util.SpawnUtil.Strategy.ON_TOP_OF_COLLIDER);
            helper.assertTrue(blocked.isEmpty(), "Desert candidate must not spawn a Hound");
            commands.execute(fill + "minecraft:plains", source);
            var allowed = de.cadentem.pale_hound.util.Utils.trySpawnMob(player, ModEntityTypes.PALE_HOUND.get(),
                net.minecraft.world.entity.MobSpawnType.TRIGGERED, level, pos, 1, 0, 1,
                net.minecraft.util.SpawnUtil.Strategy.ON_TOP_OF_COLLIDER);
            helper.assertTrue(allowed.isPresent(), "Same position in an allowed biome should spawn successfully");
            allowed.ifPresent(PaleHoundEntity::discard);
        } finally {
            ServerConfig.SPAWN_DISTANCE.set(distance);
            ServerConfig.CHECK_PATH_TO_SPAWN.set(checkPath);
            commands.execute(fill + originalBiome, source);
            level.setDayTime(originalTime);
            level.updateSkyBrightness();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void soundCuePayloadRoundTrip(GameTestHelper helper) {
        var source = java.util.UUID.randomUUID();
        for (var kind : de.cadentem.pale_hound.network.HoundSoundCue.Kind.values()) {
            var packet = new de.cadentem.pale_hound.network.HoundSoundCue(kind, source);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                de.cadentem.pale_hound.network.HoundSoundCue.STREAM_CODEC.encode(buffer, packet);
                helper.assertTrue(packet.equals(de.cadentem.pale_hound.network.HoundSoundCue.STREAM_CODEC.decode(buffer))
                    && buffer.readableBytes() == 0, "Cue kind/source changed in transit");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void soundsFollowExistingEncounterStates(GameTestHelper helper) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var level = helper.getLevel();
        var hound = spawn(helper);
        hound.setNoAi(true);
        hound.setNoGravity(true);
        var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "AudioStateTest"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(hound.position().add(2, 0, 0));
        var events = new java.util.ArrayList<de.cadentem.pale_hound.network.HoundSoundCue.Kind>();
        var audio = new de.cadentem.pale_hound.entities.HoundAudioController(hound) {
            @Override protected void emit(net.minecraft.world.entity.player.Player target, de.cadentem.pale_hound.network.HoundSoundCue.Kind kind) {
                helper.assertTrue(target == player, "Encounter cue went to the wrong listener");
                events.add(kind);
            }
        };
        var pos = hound.blockPosition();
        var originalBiome = level.getBiome(pos).unwrapKey().orElseThrow().location();
        var source = level.getServer().createCommandSourceStack().withLevel(level).withPermission(4).withSuppressedOutput();
        String fill = "fillbiome " + (pos.getX() - 2) + " " + (pos.getY() - 2) + " " + (pos.getZ() - 2) + " "
            + (pos.getX() + 2) + " " + (pos.getY() + 2) + " " + (pos.getZ() + 2) + " ";
        var commands = level.getServer().getCommands().getDispatcher();
        commands.execute(fill + "minecraft:lush_caves", source);
        helper.setBlock(2, 5, 2, net.minecraft.world.level.block.Blocks.STONE);
        helper.runAfterDelay(20, () -> {
            try {
                helper.assertTrue(!de.cadentem.pale_hound.util.Utils.isOnSurface(hound), "Underground fixture was classified as surface");
                hound.setTarget(player);
                hound.currentRoll = de.cadentem.pale_hound.entities.goals.Roll.STARE;
                hound.setAggressive(false);
                audio.playerDetected(player);
                audio.playerDetected(player);
                helper.assertTrue(events.equals(java.util.List.of(de.cadentem.pale_hound.network.HoundSoundCue.Kind.SEES_PLAYER)),
                    "Player detection should play once, using the general cave cue");
                audio.tick();
                helper.assertTrue(events.size() == 1, "Stalking cue should wait for the detection clip");
                hound.tickCount += 461;
                audio.tick();
                helper.assertTrue(events.getLast() == de.cadentem.pale_hound.network.HoundSoundCue.Kind.STALKING_UNDERGROUND,
                    "Passive cave approach should play underground stalking audio");
                helper.assertTrue(!hound.isAggressive() && hound.getTarget() == player, "Audio must not change target or combat state");
                audio.tick();
                helper.assertTrue(events.size() == 2, "Long stalking clips must not replay every tick");
                hound.setAggressive(true);
                audio.tick();
                audio.tick();
                helper.assertTrue(events.size() == 3 && events.getLast() == de.cadentem.pale_hound.network.HoundSoundCue.Kind.STOP_STALKING,
                    "Starting combat must stop stalking playback exactly once");
                hound.setAggressive(false);
                hound.currentRoll = de.cadentem.pale_hound.entities.goals.Roll.FLEE;
                hound.tickCount += 1000;
                audio.tick();
                helper.assertTrue(events.size() == 3, "Fleeing must not play stalking clips");
                helper.setBlock(2, 5, 2, net.minecraft.world.level.block.Blocks.AIR);
                commands.execute(fill + originalBiome, source);
                audio.playerDetected(player);
                helper.assertTrue(events.getLast() == de.cadentem.pale_hound.network.HoundSoundCue.Kind.SEES_PLAYER_SURFACE,
                    "Aboveground detection should choose the surface-specific pool");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                throw new RuntimeException(error);
            } finally {
                try { commands.execute(fill + originalBiome, source); }
                catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) { throw new RuntimeException(error); }
                hound.discard();
                player.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30)
    public static void commandEncounterBypassesGatesAndPreservesTimers(GameTestHelper helper) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var level = helper.getLevel();
        var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ForcedEncounterTest"));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 64))));
        level.addNewPlayer(player);
        var dispatcher = level.getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2);
        String dimension = level.dimension().location().toString();
        var spawnBefore = de.cadentem.pale_hound.PaleHound.getTimer(dimension, "spawn");
        var noiseBefore = de.cadentem.pale_hound.PaleHound.getTimer(dimension, "noise");
        double chance = ServerConfig.SPAWN_CHANCE_PER_TICK.get();
        int maximum = ServerConfig.MAXIMUM_AMOUNT.get();
        boolean surface = ServerConfig.ALLOW_SURFACE_SPAWN.get();
        var biomes = ServerConfig.SURFACE_BIOMES.get();
        var dimensions = ServerConfig.DIMENSION_WHITELIST.get();
        PaleHoundEntity restored = null;
        try {
            boolean denied = false;
            try { dispatcher.execute("pale_hound encounter", source.withPermission(0)); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            helper.assertTrue(denied, "Creative alone must not grant the force-encounter command");
            ServerConfig.SPAWN_CHANCE_PER_TICK.set(0.0);
            ServerConfig.MAXIMUM_AMOUNT.set(0);
            ServerConfig.ALLOW_SURFACE_SPAWN.set(false);
            ServerConfig.SURFACE_BIOMES.set(java.util.List.of("minecraft:plains"));
            ServerConfig.DIMENSION_WHITELIST.set(java.util.List.of());
            helper.assertTrue(dispatcher.execute("pale_hound encounter", source) == 1, "Forced encounter failed with natural spawning disabled");
            var hounds = level.getEntities(ModEntityTypes.PALE_HOUND.get(), h -> h.isCommandEncounterTarget(player));
            helper.assertTrue(hounds.size() == 1, "Command must create exactly one encounter");
            var hound = hounds.getFirst();
            hound.setNoAi(true);
            helper.assertTrue(hound.distanceToSqr(player) >= 32 * 32, "Forced encounter spawned too close");
            helper.assertTrue(hound.isCommandEncounter() && hound.hasSpawned && !hound.isInvisible(), "Encounter did not initialize natural behavior");
            helper.assertTrue(hound.isSurfaceStalking() || hound.getTarget() == player, "Creative command recipient was ignored");
            helper.assertTrue(level.noCollision(hound, hound.getBoundingBox().deflate(0.00001)), "Forced encounter spawned inside a wall");
            var tag = new net.minecraft.nbt.CompoundTag();
            hound.addAdditionalSaveData(tag);
            restored = ModEntityTypes.PALE_HOUND.get().create(level);
            restored.readAdditionalSaveData(tag);
            helper.assertTrue(restored.isCommandEncounter() && restored.isCommandEncounterTarget(player), "Timer exemption did not survive NBT reload");
            helper.assertTrue(spawnBefore.equals(de.cadentem.pale_hound.PaleHound.getTimer(dimension, "spawn"))
                && noiseBefore.equals(de.cadentem.pale_hound.PaleHound.getTimer(dimension, "noise")), "Command reset a scheduled timer");
            if (!hound.isSurfaceStalking()) {
                hound.setTarget(null);
                hound.currentRoll = de.cadentem.pale_hound.entities.goals.Roll.STROLL;
                helper.assertTrue(hound.tryStartSurfaceStalking(player), "Forced Creative target should be valid for stalking");
            }
            hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 64))));
            player.setPos(hound.position().add(40, 0, 0));
            Vec3 look = hound.getEyePosition().subtract(player.getEyePosition());
            player.setYRot((float)Math.toDegrees(Math.atan2(-look.x, look.z)));
            player.setYHeadRot(player.getYRot());
            player.setXRot((float)-Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))));
            for (int i = 0; i < 160; ++i) hound.tick();
            helper.assertTrue(hound.isSurfaceStalkRetreating(), "Command encounter should react naturally to a Creative player's gaze");
            hound.setPos(hound.position().add(1024, 0, 0));
            for (int i = 0; i < 60; ++i) hound.tick();
            helper.assertTrue(hound.isRemoved(), "Command encounter should still finish its retreat and despawn");
            helper.assertTrue(spawnBefore.equals(de.cadentem.pale_hound.PaleHound.getTimer(dimension, "spawn"))
                && noiseBefore.equals(de.cadentem.pale_hound.PaleHound.getTimer(dimension, "noise")), "Spotted command encounter changed scheduled timers");
            helper.assertTrue(dispatcher.execute("pale_hound encounter @s", source) == 1, "Explicit player argument failed");
        } finally {
            for (var hound : level.getEntities(ModEntityTypes.PALE_HOUND.get(), h -> h.isCommandEncounterTarget(player))) hound.discard();
            if (restored != null) restored.discard();
            level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            ServerConfig.SPAWN_CHANCE_PER_TICK.set(chance);
            ServerConfig.MAXIMUM_AMOUNT.set(maximum);
            ServerConfig.ALLOW_SURFACE_SPAWN.set(surface);
            ServerConfig.SURFACE_BIOMES.set(biomes);
            ServerConfig.DIMENSION_WHITELIST.set(dimensions);
        }
        helper.succeed();
    }

    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, timeoutTicks = 100)
    public static void surfaceRetreatRunsAwayOnOneRoute(GameTestHelper helper) {
        var level = helper.getLevel();
        var hound = ModEntityTypes.PALE_HOUND.get().create(level);
        Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 64)));
        hound.setPos(start);
        level.addFreshEntity(hound);
        var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "RetreatTest"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(start.add(-40, 0, 0));
        level.addNewPlayer(player);
        helper.assertTrue(hound.tryStartSurfaceStalking(player), "Could not start physical retreat fixture: houndSurface=" + de.cadentem.pale_hound.util.Utils.isOnSurface(hound) + " playerSurface=" + de.cadentem.pale_hound.util.Utils.isOnSurface(player));
        hound.setNoAi(true);
        for (int i = 0; i < 160; ++i) hound.tick();
        hound.setNoAi(false);
        Vec3 look = hound.getEyePosition().subtract(player.getEyePosition());
        player.setYRot((float)Math.toDegrees(Math.atan2(-look.x, look.z)));
        player.setYHeadRot(player.getYRot());
        player.setXRot((float)-Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))));
        var firstRoute = new java.util.concurrent.atomic.AtomicReference<net.minecraft.world.level.pathfinder.Path>();
        helper.onEachTick(() -> {
            if (hound.isRemoved()) return;
            var route = hound.getNavigation().getPath();
            if (route != null && !route.isDone() && hound.isSurfaceStalkRetreating()) {
                if (firstRoute.get() != null && !firstRoute.get().isDone())
                    helper.assertTrue(firstRoute.get() == route, "Working retreat route was replaced and could cause circling");
                firstRoute.set(route);
            }
            helper.assertTrue(Math.abs(hound.getZ() - start.z) < 1, "Retreat circled sideways on open ground");
            helper.assertTrue(!hound.canAttack(player), "Retreating Hound should not attack");
        });
        helper.runAfterDelay(45, () -> {
            try {
                helper.assertTrue(!hound.isRemoved() && hound.isSurfaceStalkRetreating(), "Retreat did not remain active");
                helper.assertTrue(hound.getX() > start.x + 4, "Hound did not physically run away from the player");
                helper.assertTrue(firstRoute.get() != null, "No retreat route was started");
            } finally { hound.discard(); level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); }
            helper.succeed();
        });
    }

    @GameTest(template = "encounter_test", skyAccess = true, setupTicks = 30, timeoutTicks = 120)
    public static void normalFleeDoesNotCompeteWithMelee(GameTestHelper helper) {
        var level = helper.getLevel();
        var hound = ModEntityTypes.PALE_HOUND.get().create(level);
        Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(32, 2, 32)));
        hound.setPos(start);
        level.addFreshEntity(hound);
        var sheep = net.minecraft.world.entity.EntityType.SHEEP.create(level);
        sheep.setNoAi(true);
        sheep.setPos(start.add(-12, 0, 0));
        level.addFreshEntity(sheep);
        hound.setTarget(sheep);
        hound.currentRoll = de.cadentem.pale_hound.entities.goals.Roll.FLEE;
        helper.assertTrue(!new de.cadentem.pale_hound.entities.goals.PaleHoundChaseGoal(hound, true).canContinueToUse(),
            "Chase movement must yield when the roll switches to flee");
        float health = sheep.getHealth();
        helper.onEachTick(() -> {
            if (!hound.isRemoved()) helper.assertTrue(!hound.canAttack(sheep) && !hound.doHurtTarget(sheep), "Fleeing Hound continued melee attacks");
        });
        helper.runAfterDelay(50, () -> {
            try {
                helper.assertTrue(!hound.isRemoved() && hound.isFleeing, "Normal flee should be active after its initial stare");
                helper.assertTrue(hound.getX() > start.x + 3 && Math.abs(hound.getZ() - start.z) < 1, "Normal flee did not move away on a stable heading: position=" + hound.position() + " start=" + start + " path=" + hound.getNavigation().getPath() + " crawl=" + hound.isCrawling() + " speed=" + hound.getSpeed());
                helper.assertTrue(sheep.getHealth() == health, "Flee damaged its previous target");
            } finally { hound.discard(); sheep.discard(); }
            helper.succeed();
        });
    }

    @GameTest(template = "encounter_test", skyAccess = true, setupTicks = 30)
    public static void retreatFlanksWallWithoutReturningToThreat(GameTestHelper helper) {
        var hound = ModEntityTypes.PALE_HOUND.get().create(helper.getLevel());
        hound.setNoAi(true);
        Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(32, 2, 32)));
        hound.setPos(start);
        helper.getLevel().addFreshEntity(hound);
        for (int y = 2; y <= 6; ++y) for (int z = 29; z <= 35; ++z)
            helper.setBlock(36, y, z, net.minecraft.world.level.block.Blocks.STONE);
        Vec3 threat = start.add(-20, 0, 0);
        try {
            var path = new de.cadentem.pale_hound.entities.navigation.HoundRetreat().findPath(hound, threat);
            helper.assertTrue(path != null && path.canReach(), "Escape planner could not flank a simple wall");
            boolean flanked = false;
            for (int i = 0; i < path.getNodeCount(); ++i) {
                Vec3 node = Vec3.atBottomCenterOf(path.getNode(i).asBlockPos());
                if (Math.abs(node.z - start.z) > 3) flanked = true;
                helper.assertTrue(Math.hypot(node.x - threat.x, node.z - threat.z) >= 19,
                    "Escape route looped back toward the threat");
            }
            helper.assertTrue(flanked && path.getTarget().getX() >= start.x + 4, "Escape route did not go around the wall and away");
        } finally { hound.discard(); }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void crawlingRemainsVisibleAtEncounterDistance(GameTestHelper helper) {
        var hound = spawn(helper);
        try {
            helper.assertTrue(hound.shouldRenderAtSqrDistance(48 * 48), "Standing Hound should be visible at natural spawn distance");
            hound.setCrawling(true);
            helper.assertTrue(hound.shouldRenderAtSqrDistance(48 * 48), "Crawling Hound must not disappear visually at natural spawn distance");
            helper.assertTrue(hound.getBoundingBoxForCulling().maxY >= hound.getY() + 2.15,
                "Culling bounds must include the tall crouched model's head");
            helper.assertTrue(hound.getBoundingBox().getYsize() == 0.5, "Visibility bounds must not enlarge tunnel collision dimensions");
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
