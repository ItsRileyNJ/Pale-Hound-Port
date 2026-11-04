package de.cadentem.pale_hound.gametest;

import com.mojang.serialization.Dynamic;
import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.difficulty.StoryDifficulty;
import de.cadentem.pale_hound.network.StoryDifficultyState;
import de.cadentem.pale_hound.registry.ModEntityTypes;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("pale_hound")
@PrefixGameTestTemplate(false)
public final class StoryGameTests {
    /** Separate batch: this test intentionally changes the entire server's difficulty and config. */
    @GameTest(template = "port_test", batch = "story_difficulty")
    public static void storySpawnPolicyAndDifficultyLifecycle(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var server = level.getServer();
        var previous = server.getWorldData().getDifficulty();
        boolean oldStory = StoryDifficulty.isEnabled(server);
        boolean oldLock = server.getWorldData().isDifficultyLocked();
        var allowlist = ServerConfig.STORY_HOSTILE_ALLOWLIST.get();
        var spawned = new ArrayList<Entity>();
        var source = server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        try {
            server.setDifficultyLocked(false);
            StoryDifficulty.setEnabled(server, false);
            server.setDifficulty(Difficulty.NORMAL, true);
            var before = EntityType.ZOMBIE.create(level);
            helper.assertTrue(level.addFreshEntity(before), "Normal difficulty blocked a zombie");
            spawned.add(before);
            helper.assertTrue(allowlist.equals(List.of("pale_hound:pale_hound")), "Story default must allow only the Hound");
            server.getCommands().getDispatcher().execute("difficulty story", source);
            helper.assertTrue(StoryDifficulty.isEnabled(server), "Story command did not enable mode");
            helper.assertTrue(server.getWorldData().getDifficulty() == Difficulty.EASY, "Story must use Easy combat rules");
            helper.assertTrue(before.isRemoved(), "Enabling Story left an existing disallowed hostile");
            for (var type : List.of(EntityType.ZOMBIE, EntityType.CREEPER, EntityType.SPIDER, EntityType.PHANTOM,
                    EntityType.SLIME, EntityType.ENDERMAN, EntityType.PIGLIN, EntityType.GHAST, EntityType.WARDEN)) {
                var mob = type.create(level);
                helper.assertTrue(!level.addFreshEntity(mob), "Story admitted " + EntityType.getKey(type));
                mob.discard();
                for (var reason : List.of(MobSpawnType.NATURAL, MobSpawnType.SPAWNER, MobSpawnType.SPAWN_EGG, MobSpawnType.COMMAND)) {
                    var check = new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck(
                        type, level, reason, helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)), level.random, true);
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(check);
                    helper.assertTrue(check.getResult() == net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.FAIL, "Spawn placement admitted disallowed " + reason);
                }
            }
            // Both passive mobs and neutral utility/pet mobs remain available.
            for (var type : List.of(EntityType.SHEEP, EntityType.VILLAGER, EntityType.WOLF, EntityType.IRON_GOLEM, EntityType.BEE,
                    ModEntityTypes.PALE_HOUND.get())) {
                var mob = type.create(level);
                helper.assertTrue(level.addFreshEntity(mob), "Story blocked allowed/passive " + EntityType.getKey(type));
                spawned.add(mob);
            }
            var loaded = EntityType.ZOMBIE.create(level);
            var join = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(loaded, level, true);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(join);
            helper.assertTrue(join.isCanceled(), "NBT-loaded hostile bypassed Story"); loaded.discard();
            // Startup's server.properties difficulty application must not erase the saved Story flag.
            StoryDifficulty.stopped(new net.neoforged.neoforge.event.server.ServerStoppedEvent(server));
            server.setDifficulty(Difficulty.HARD, true);
            helper.assertTrue(StoryDifficulty.isEnabled(server), "Startup difficulty restoration erased saved Story");
            StoryDifficulty.started(new net.neoforged.neoforge.event.server.ServerStartedEvent(server));
            helper.assertTrue(server.getWorldData().getDifficulty() == Difficulty.EASY, "Startup did not restore Story's Easy base");
            var restored = new GameRules(new Dynamic<>(NbtOps.INSTANCE, server.getGameRules().createTag()));
            helper.assertTrue(restored.getBoolean(StoryDifficulty.RULE), "Story was not persisted in world game rules");
            ServerConfig.STORY_HOSTILE_ALLOWLIST.set(List.of("pale_hound:pale_hound", "minecraft:zombie", "optional_mod:monster"));
            var permitted = EntityType.ZOMBIE.create(level);
            helper.assertTrue(level.addFreshEntity(permitted), "Configured zombie allowlist entry was ignored"); spawned.add(permitted);
            ServerConfig.STORY_HOSTILE_ALLOWLIST.set(List.of());
            StoryDifficulty.apply(server);
            helper.assertTrue(permitted.isRemoved(), "Reloading allowlist left a newly disallowed hostile");
            var hound = ModEntityTypes.PALE_HOUND.get().create(level);
            helper.assertTrue(!level.addFreshEntity(hound), "An empty allowlist must block even the Hound"); hound.discard();
            ServerConfig.STORY_HOSTILE_ALLOWLIST.set(allowlist);
            server.setDifficultyLocked(true);
            server.setDifficulty(Difficulty.HARD, false);
            helper.assertTrue(StoryDifficulty.isEnabled(server), "A rejected difficulty change unlocked Story");
            server.setDifficultyLocked(false);
            server.getCommands().getDispatcher().execute("difficulty easy", source);
            helper.assertTrue(!StoryDifficulty.isEnabled(server), "Selecting Easy did not leave Story with the same base difficulty");
            var normal = EntityType.ZOMBIE.create(level);
            helper.assertTrue(level.addFreshEntity(normal), "Leaving Story did not restore hostile spawning"); spawned.add(normal);
            StoryDifficulty.setEnabled(server, true);
            server.setDifficulty(Difficulty.HARD, false);
            helper.assertTrue(!StoryDifficulty.isEnabled(server) && server.getWorldData().getDifficulty() == Difficulty.HARD,
                "Vanilla GUI difficulty changes must leave Story");
            helper.assertTrue(server.getCommands().getDispatcher().parse("difficulty story", source.withPermission(0)).getReader().canRead(),
                "Non-operators should not have the Story command");
        } finally {
            spawned.forEach(Entity::discard);
            ServerConfig.STORY_HOSTILE_ALLOWLIST.set(allowlist);
            StoryDifficulty.setEnabled(server, false);
            server.setDifficulty(previous, true);
            if (oldStory) StoryDifficulty.setEnabled(server, true);
            server.setDifficultyLocked(oldLock);
        }
        helper.succeed();
    }

    @GameTest(template = "port_test")
    public static void storyStatePayloadRoundTrip(GameTestHelper helper) {
        for (boolean enabled : List.of(true, false)) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var state = new StoryDifficultyState(enabled);
                StoryDifficultyState.STREAM_CODEC.encode(buffer, state);
                helper.assertTrue(StoryDifficultyState.STREAM_CODEC.decode(buffer).equals(state), "Story state codec changed");
                helper.assertTrue(!buffer.isReadable(), "Story codec left unread bytes");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }
}
