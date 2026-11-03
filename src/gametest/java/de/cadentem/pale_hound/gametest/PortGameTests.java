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
            while (!hound.isSurfaceStalkRetreating() && ticks++ < 901) hound.tick();
            helper.assertTrue(ticks + 2 >= 600 && ticks + 2 <= 900, "Stalking duration should be 30–45 seconds");
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
                net.minecraft.world.entity.EntityType.VILLAGER, net.minecraft.world.entity.EntityType.IRON_GOLEM)) {
                var target = (net.minecraft.world.entity.LivingEntity)type.create(helper.getLevel());
                helper.assertTrue(de.cadentem.pale_hound.util.PaleHoundTargets.isAllowed(target), "Allowed target rejected: " + type);
                hound.setTarget(target);
                helper.assertTrue(hound.getTarget() == target, "Allowed target was not assigned");
                target.discard();
            }
            for (var type : java.util.List.of(net.minecraft.world.entity.EntityType.COW, net.minecraft.world.entity.EntityType.ZOMBIE,
                net.minecraft.world.entity.EntityType.PILLAGER, net.minecraft.world.entity.EntityType.WOLF,
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
