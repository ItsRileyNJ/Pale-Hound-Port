package de.cadentem.pale_hound.client;

import de.cadentem.pale_hound.difficulty.StoryDifficulty;
import de.cadentem.pale_hound.network.StoryDifficultyRequest;
import de.cadentem.pale_hound.network.StoryDifficultyState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.LockIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.EqualSpacingLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.world.Difficulty;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class StoryDifficultyClient {
    private static boolean enabled;
    private StoryDifficultyClient() {}
    public static boolean isEnabled() { return enabled; }
    public static void handle(StoryDifficultyState packet) {
        enabled = packet.enabled();
        var client = Minecraft.getInstance();
        if (client.screen instanceof OptionsScreen screen) screen.init(client, screen.width, screen.height);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { enabled = false; }

    public enum Choice {
        PEACEFUL(Difficulty.PEACEFUL), STORY(null), EASY(Difficulty.EASY), NORMAL(Difficulty.NORMAL), HARD(Difficulty.HARD);
        private final Difficulty vanilla;
        Choice(Difficulty vanilla) { this.vanilla = vanilla; }
        public Component label() {
            return this == STORY ? StoryDifficulty.name() : Component.translatable("options.difficulty." + vanilla.getKey());
        }
        public static Choice current(Difficulty difficulty) {
            return current(difficulty, enabled);
        }
        public static Choice current(Difficulty difficulty, boolean story) {
            if (story) return STORY;
            for (var choice : values()) if (choice.vanilla == difficulty) return choice;
            return EASY;
        }
    }

    public static CycleButton<Choice> creationSelector(WorldCreationUiState state) {
        var selector = CycleButton.<Choice>builder(Choice::label).withValues(Choice.values())
            .withInitialValue(Choice.current(state.getDifficulty(), state.getGameRules().getBoolean(StoryDifficulty.RULE)))
            .create(0, 0, 210, 20, Component.translatable("options.difficulty"), (button, choice) -> {
                state.getGameRules().getRule(StoryDifficulty.RULE).set(choice == Choice.STORY, null);
                state.setDifficulty(choice == Choice.STORY ? Difficulty.EASY : choice.vanilla);
            });
        state.addListener(updated -> {
            if (updated.isHardcore()) updated.getGameRules().getRule(StoryDifficulty.RULE).set(false, null);
            boolean story = updated.getGameRules().getBoolean(StoryDifficulty.RULE);
            selector.setValue(Choice.current(updated.getDifficulty(), story));
            selector.active = !updated.isHardcore();
            selector.setTooltip(Tooltip.create(story ? Component.translatable("pale_hound.story.description") : updated.getDifficulty().getInfo()));
        });
        return selector;
    }

    /** Matches vanilla's 150px difficulty/lock layout, adding Story to the existing cycle. */
    public static LayoutElement selector(Screen parent, Minecraft client) {
        var data = client.level.getLevelData();
        boolean locked = data.isDifficultyLocked();
        var difficulty = CycleButton.<Choice>builder(Choice::label).withValues(Choice.values())
            .withInitialValue(Choice.current(data.getDifficulty()))
            .withTooltip(choice -> choice == Choice.STORY ? Tooltip.create(Component.translatable("pale_hound.story.description")) : null)
            .create(0, 0, 130, 20, Component.translatable("options.difficulty"), (button, choice) -> {
                if (choice == Choice.STORY) PacketDistributor.sendToServer(new StoryDifficultyRequest());
                else client.getConnection().send(new ServerboundChangeDifficultyPacket(choice.vanilla));
            });
        difficulty.active = !locked;
        var lock = new LockIconButton(0, 0, button -> client.setScreen(new ConfirmScreen(confirmed -> {
            client.setScreen(parent);
            if (confirmed) {
                client.getConnection().send(new ServerboundLockDifficultyPacket(true));
                lockSelector(parent, client);
            }
        }, Component.translatable("difficulty.lock.title"), Component.translatable("difficulty.lock.question",
            enabled ? StoryDifficulty.name() : data.getDifficulty().getDisplayName()))));
        lock.setLocked(locked); lock.active = !locked;
        var row = new EqualSpacingLayout(150, 0, EqualSpacingLayout.Orientation.HORIZONTAL);
        row.addChild(difficulty); row.addChild(lock);
        return row;
    }
    private static void lockSelector(Screen parent, Minecraft client) {
        client.level.getLevelData().setDifficultyLocked(true);
        parent.init(client, parent.width, parent.height);
    }
}
