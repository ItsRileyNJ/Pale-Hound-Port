package de.cadentem.pale_hound.gametest;

import de.cadentem.pale_hound.PaleHound;
import de.cadentem.pale_hound.client.StoryDifficultyClient;
import de.cadentem.pale_hound.client.StoryDifficultyClient.Choice;
import de.cadentem.pale_hound.difficulty.StoryDifficulty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "pale_hound", value = Dist.CLIENT)
public final class ClientStorySmoke {
    private static int phase, ticks, marked;
    private static volatile boolean prepared;
    private static CycleButton<?> selector(Screen screen) {
        return screen.children().stream().filter(widget -> widget instanceof CycleButton<?> button && button.getValue() instanceof Choice)
            .map(widget -> (CycleButton<?>)widget).findFirst().orElseThrow(() -> new AssertionError("Story difficulty selector missing in " + screen));
    }
    private static void selectStory(CycleButton<?> button) {
        for (int i = 0; i < 5 && button.getValue() != Choice.STORY; ++i) button.onPress();
        if (button.getValue() != Choice.STORY) throw new AssertionError("Story is absent from cycle");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("pale_hound.clientStorySmoke")) return;
        var client = Minecraft.getInstance();
        if (++ticks > 1800) throw new AssertionError("Story client smoke timed out at " + phase);
        client.options.pauseOnLostFocus = false;
        if (phase == 0 && client.level != null && client.player != null && client.getSingleplayerServer() != null) {
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                server.setDifficultyLocked(false);
                StoryDifficulty.setEnabled(server, false);
                server.setDifficulty(Difficulty.PEACEFUL, true);
                prepared = true;
            });
            phase = 1;
        } else if (phase == 1 && prepared && client.level.getDifficulty() == Difficulty.PEACEFUL) {
            client.setScreen(new OptionsScreen(null, client.options));
            selectStory(selector(client.screen)); phase = 2;
        } else if (phase == 2 && StoryDifficultyClient.isEnabled() && client.level.getDifficulty() == Difficulty.EASY) {
            var button = selector(client.screen);
            if (button.getValue() != Choice.STORY || !button.getMessage().getString().contains("Story")) throw new AssertionError("Story label was not synced");
            phase = 3; marked = ticks;
        } else if (phase == 3 && ticks > marked + 20) {
            Screenshot.grab(client.gameDirectory, "story-options.png", client.getMainRenderTarget(), message -> {});
            selector(client.screen).onPress(); // Story -> Easy, same vanilla difficulty ID.
            phase = 4;
        } else if (phase == 4 && !StoryDifficultyClient.isEnabled()) {
            if (selector(client.screen).getValue() != Choice.EASY) throw new AssertionError("Leaving Story did not update the selector");
            client.setScreen(null);
            client.level.disconnect(); client.disconnect();
            CreateWorldScreen.openFresh(client, new TitleScreen());
            phase = 5;
        } else if (phase == 5 && client.screen instanceof CreateWorldScreen screen) {
            selectStory(selector(screen));
            var state = screen.getUiState();
            if (!state.getGameRules().getBoolean(StoryDifficulty.RULE) || state.getDifficulty() != Difficulty.EASY)
                throw new AssertionError("New world Story selection did not save its rule and base difficulty");
            phase = 6; marked = ticks;
        } else if (phase == 6 && ticks > marked + 20 && client.screen instanceof CreateWorldScreen screen) {
            Screenshot.grab(client.gameDirectory, "story-create-world.png", client.getMainRenderTarget(), message -> {});
            var state = screen.getUiState();
            selector(screen).onPress();
            if (state.getGameRules().getBoolean(StoryDifficulty.RULE)) throw new AssertionError("New world Easy did not leave Story");
            selectStory(selector(screen));
            state.setGameMode(WorldCreationUiState.SelectedGameMode.HARDCORE);
            if (state.getGameRules().getBoolean(StoryDifficulty.RULE) || selector(screen).active)
                throw new AssertionError("Hardcore should disable Story like other difficulty overrides");
            if (StoryDifficultyClient.isEnabled()) throw new AssertionError("Story client state leaked after logout");
            PaleHound.LOG.info("CLIENT STORY SMOKE PASSED: actual Options cycle, server packet activation/sync, Easy exit, new-world Story/Easy selection, Hardcore guard and logout verified");
            phase = 7; client.stop();
        }
    }
}
