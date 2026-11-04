package de.cadentem.pale_hound.mixin;

import de.cadentem.pale_hound.difficulty.StoryDifficulty;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MixinServerDifficulty {
    @Inject(method = "setDifficulty", at = @At("TAIL"))
    private void pale_hound$ordinaryDifficulty(Difficulty difficulty, boolean force, CallbackInfo callback) {
        var server = (MinecraftServer)(Object)this;
        if (force || !server.getWorldData().isDifficultyLocked()) StoryDifficulty.onVanillaDifficultyChanged(server);
    }
}
