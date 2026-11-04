package de.cadentem.pale_hound.mixin;

import de.cadentem.pale_hound.client.StoryDifficultyClient;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Replace only the rendered difficulty widget; vanilla's private listener keeps its own hidden widget. */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class MixinCreateWorldStoryDifficulty {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;Lnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;", ordinal = 2))
    private LayoutElement pale_hound$creationDifficulty(GridLayout.RowHelper row, LayoutElement original,
            LayoutSettings settings, CreateWorldScreen screen) {
        row.addChild(StoryDifficultyClient.creationSelector(screen.getUiState()), settings);
        return original;
    }
}
