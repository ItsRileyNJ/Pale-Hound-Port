package de.cadentem.pale_hound.mixin;

import de.cadentem.pale_hound.client.StoryDifficultyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionsScreen.class)
public abstract class MixinOptionsStoryDifficulty {
    @Inject(method = "createOnlineButton", at = @At("HEAD"), cancellable = true)
    private void pale_hound$storySelector(CallbackInfoReturnable<LayoutElement> callback) {
        var client = Minecraft.getInstance();
        if (client.level != null && client.hasSingleplayerServer() && !client.level.getLevelData().isHardcore())
            callback.setReturnValue(StoryDifficultyClient.selector((Screen)(Object)this, client));
    }
}
