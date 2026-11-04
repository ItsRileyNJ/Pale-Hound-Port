package de.cadentem.pale_hound.difficulty;

import de.cadentem.pale_hound.config.ServerConfig;
import de.cadentem.pale_hound.network.StoryDifficultyState;
import java.util.ArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import static net.minecraft.commands.Commands.literal;

/** A saved world mode over Easy; vanilla difficulty IDs and save/network codecs stay intact. */
@EventBusSubscriber(modid = "pale_hound")
public final class StoryDifficulty {
    public static final GameRules.Key<GameRules.BooleanValue> RULE = GameRules.register("paleHoundStoryMode",
        GameRules.Category.MOBS, GameRules.BooleanValue.create(false, (server, value) -> apply(server)));
    private static boolean applying;
    private static MinecraftServer runningServer;

    private StoryDifficulty() {}
    public static void init() {} // Register before any world creates its GameRules instance.
    public static boolean isEnabled(MinecraftServer server) { return server.getGameRules().getBoolean(RULE); }
    public static Component name() { return Component.translatable("difficulty.pale_hound.story"); }
    public static void setEnabled(MinecraftServer server, boolean enabled) {
        server.getGameRules().getRule(RULE).set(enabled, server);
    }
    public static boolean isAllowed(EntityType<?> type) {
        return ServerConfig.STORY_HOSTILE_ALLOWLIST.get().contains(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }
    public static boolean isBlocked(Entity entity) {
        return entity instanceof Mob && (entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER)
            && !isAllowed(entity.getType());
    }
    public static void apply(MinecraftServer server) {
        if (isEnabled(server)) {
            if (server.isHardcore()) { setEnabled(server, false); return; }
            applying = true;
            try { server.setDifficulty(Difficulty.EASY, true); } finally { applying = false; }
            // Switching modes and reloading the allowlist also removes existing disallowed hostiles.
            for (var level : server.getAllLevels()) {
                var remove = new ArrayList<Entity>();
                for (var entity : level.getAllEntities()) if (isBlocked(entity)) remove.add(entity);
                remove.forEach(Entity::discard);
            }
        }
        PacketDistributor.sendToAllPlayers(new StoryDifficultyState(isEnabled(server)));
    }
    /** Called only after vanilla has accepted an ordinary difficulty change. */
    public static void onVanillaDifficultyChanged(MinecraftServer server) {
        if (server == runningServer && !applying && isEnabled(server)) setEnabled(server, false);
    }
    public static boolean mayUseSelector(ServerPlayer player) {
        var server = player.getServer();
        return server != null && (player.hasPermissions(2) || server.isSingleplayerOwner(player.getGameProfile()))
            && !server.isHardcore() && !server.getWorldData().isDifficultyLocked();
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        runningServer = event.getServer();
        apply(event.getServer());
    }
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        if (runningServer == event.getServer()) runningServer = null;
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            PacketDistributor.sendToPlayer(player, new StoryDifficultyState(isEnabled(player.getServer())));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void placement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (isEnabled(event.getLevel().getLevel().getServer()) && event.getEntityType().getCategory() == MobCategory.MONSTER
            && !isAllowed(event.getEntityType())) event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && isEnabled(event.getLevel().getServer()) && isBlocked(event.getEntity()))
            event.setCanceled(true);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var root = literal("difficulty").requires(source -> source.hasPermission(2));
        root.executes(context -> {
            var source = context.getSource();
            var difficulty = source.getServer().getWorldData().getDifficulty();
            source.sendSuccess(() -> Component.translatable("commands.difficulty.query",
                isEnabled(source.getServer()) ? name() : difficulty.getDisplayName()), false);
            return difficulty.getId();
        });
        root.then(literal("story").executes(context -> {
            var source = context.getSource();
            if (source.getServer().isHardcore()) {
                source.sendFailure(Component.translatable("pale_hound.story.hardcore")); return 0;
            }
            setEnabled(source.getServer(), true);
            source.sendSuccess(() -> Component.translatable("commands.difficulty.success", name()), true);
            return 1;
        }));
        for (Difficulty difficulty : Difficulty.values()) root.then(literal(difficulty.getKey()).executes(context -> {
            var source = context.getSource();
            boolean wasStory = isEnabled(source.getServer());
            if (wasStory) setEnabled(source.getServer(), false);
            if (wasStory && source.getServer().getWorldData().getDifficulty() == difficulty) {
                source.sendSuccess(() -> Component.translatable("commands.difficulty.success", difficulty.getDisplayName()), true);
                return 0;
            }
            return net.minecraft.server.commands.DifficultyCommand.setDifficulty(source, difficulty);
        }));
        event.getDispatcher().register(root);
    }
}
