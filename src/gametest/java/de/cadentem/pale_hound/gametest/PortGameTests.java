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
