package de.cadentem.pale_hound.gametest;

import com.mojang.authlib.GameProfile;
import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.entities.hunting.HoundHazardLearning.Hazard;
import de.cadentem.pale_hound.entities.hunting.HoundHazardMemory;
import de.cadentem.pale_hound.entities.goals.PaleHoundHazardAvoidGoal;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("pale_hound")
@PrefixGameTestTemplate(false)
public final class HazardLearningGameTests {
    /** One isolated batch because the individual/shared setting belongs to the whole server. */
    @GameTest(template = "stalking_test", skyAccess = true, setupTicks = 30, batch = "hazard_learning")
    public static void actualDamageTeachesPersistentAvoidanceWithoutStartingKnowledge(GameTestHelper helper) {
        boolean oldShare = ServerConfig.SHARE_HAZARD_LEARNING.get();
        ServerConfig.SHARE_HAZARD_LEARNING.set(false);
        var level = helper.getLevel();
        var hound = ModEntityTypes.PALE_HOUND.get().create(level); hound.setNoAi(true);
        hound.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(64, 2, 64)))); level.addFreshEntity(hound);
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "HazardTest"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(hound.position().add(10, 0, 0)); level.addNewPlayer(player);
        var tnt = new PrimedTnt(net.minecraft.world.entity.EntityType.TNT, level); tnt.setNoGravity(true); tnt.setFuse(1000);
        tnt.setPos(hound.position().add(20, 0, 0)); level.addFreshEntity(tnt);
        var restored = ModEntityTypes.PALE_HOUND.get().create(level);
        CompoundTag originalWorldMemory = HoundHazardMemory.get(level).save(new CompoundTag(), level.registryAccess());
        de.cadentem.pale_hound.entities.PaleHoundEntity laterEncounter = null;
        try {
            var learning = hound.getHazardLearning();
            helper.assertTrue(learning.score(Hazard.LAVA) == 0 && learning.score(Hazard.EXPLOSIVES) == 0 && learning.score(Hazard.WEBS) == 0, "Fresh Hound had hazard knowledge");
            helper.assertTrue(hound.getPathfindingMalus(PathType.LAVA) == 0 && hound.getPathfindingMalus(PathType.DAMAGE_FIRE) == 0,
                "Unhurt Hound already treated heat as dangerous");
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.LAVA_BUCKET));
            helper.assertTrue(!learning.threateningItem(player), "Fresh Hound feared a held lava bucket");
            helper.assertTrue(!learning.dangerousExplosiveAt(tnt.blockPosition()), "Fresh Hound knew primed TNT was dangerous");
            float health = hound.getHealth(); hound.hurt(hound.damageSources().lava(), 4);
            helper.assertTrue(hound.getHealth() < health && learning.score(Hazard.LAVA) == 1, "Actual lava damage did not teach avoidance");
            helper.assertTrue(hound.getPathfindingMalus(PathType.LAVA) > 0, "Lava path costs did not change after damage");
            hound.invulnerableTime = 0; hound.hurt(hound.damageSources().lava(), 4);
            helper.assertTrue(learning.threateningItem(player), "Repeated lava injuries did not teach caution around lava buckets");
            learning.damaged(hound.damageSources().lava(), 0);
            helper.assertTrue(learning.score(Hazard.LAVA) == 2, "Zero/canceled damage taught extra fear");
            helper.setBlock(64, 2, 64, Blocks.COBWEB);
            hound.invulnerableTime = 0; hound.hurt(hound.damageSources().generic(), 2);
            helper.assertTrue(learning.score(Hazard.WEBS) == 1, "Being hurt while webbed did not teach the trap association");
            helper.setBlock(64, 2, 64, Blocks.AIR);
            hound.invulnerableTime = 0; hound.hurt(hound.damageSources().explosion(tnt, null), 4);
            helper.assertTrue(learning.score(Hazard.EXPLOSIVES) == 1 && learning.dangerousExplosiveAt(tnt.blockPosition()), "Explosion damage did not teach TNT danger");
            var context = new PathfindingContext(level, hound);
            var node = hound.getNavigation().getNodeEvaluator().getPathType(context, tnt.blockPosition().getX(), tnt.blockPosition().getY(), tnt.blockPosition().getZ());
            helper.assertTrue(node == PathType.DANGER_OTHER, "A* did not account for learned explosive risk");
            var wallPos = tnt.blockPosition().offset(0, 0, 1); level.setBlockAndUpdate(wallPos, Blocks.STONE.defaultBlockState());
            helper.assertTrue(hound.getNavigation().getNodeEvaluator().getPathType(context, wallPos.getX(), wallPos.getY(), wallPos.getZ()) == PathType.BLOCKED,
                "Explosive risk turned a solid wall into a walkable node");
            var airPos = tnt.blockPosition().above(5);
            helper.assertTrue(hound.getNavigation().getNodeEvaluator().getPathType(context, airPos.getX(), airPos.getY(), airPos.getZ()) == PathType.OPEN,
                "Explosive risk invented a floor in unsupported air");
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            tnt.setPos(hound.position().add(4, 0, 0)); hound.tickCount += 20;
            helper.assertTrue(new PaleHoundHazardAvoidGoal(hound).canUse(), "Experienced Hound did not react to nearby live TNT");
            var tag = new CompoundTag(); hound.addAdditionalSaveData(tag); restored.readAdditionalSaveData(tag);
            helper.assertTrue(restored.getHazardLearning().score(Hazard.LAVA) == 2 && restored.getHazardLearning().score(Hazard.EXPLOSIVES) == 1,
                "Individual learning was lost on save/reload");
            ServerConfig.SHARE_HAZARD_LEARNING.set(true);
            learning.learned(Hazard.FIRE);
            laterEncounter = ModEntityTypes.PALE_HOUND.get().create(level);
            helper.assertTrue(laterEncounter.getHazardLearning().score(Hazard.FIRE) > 0
                && laterEncounter.getPathfindingMalus(PathType.DAMAGE_FIRE) > 0, "Future encounter did not inherit world experience");
            var world = new HoundHazardMemory(); world.remember(Hazard.LAVA, 2); world.remember(Hazard.EXPLOSIVES, 99);
            var saved = world.save(new CompoundTag(), level.registryAccess()); var reloaded = HoundHazardMemory.load(saved);
            helper.assertTrue(reloaded.score(Hazard.LAVA) == 2 && reloaded.score(Hazard.EXPLOSIVES) == 3, "World memory did not persist bounded experience");
        } finally {
            level.getServer().overworld().getDataStorage().set("pale_hound_hazard_memory", HoundHazardMemory.load(originalWorldMemory));
            if (laterEncounter != null) laterEncounter.discard();
            hound.discard(); restored.discard(); tnt.discard(); level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            ServerConfig.SHARE_HAZARD_LEARNING.set(oldShare);
        }
        helper.succeed();
    }
}
