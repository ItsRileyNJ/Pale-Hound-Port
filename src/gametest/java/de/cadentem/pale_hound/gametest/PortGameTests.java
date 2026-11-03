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
        var target = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
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
